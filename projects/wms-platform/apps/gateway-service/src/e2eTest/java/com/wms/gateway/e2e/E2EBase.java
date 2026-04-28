package com.wms.gateway.e2e;

import com.redis.testcontainers.RedisContainer;
import com.wms.gateway.testsupport.JwksMockServer;
import com.wms.gateway.testsupport.JwtTestHelper;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base infrastructure for gateway ↔ master live-pair e2e tests.
 *
 * <p>Boots onto a shared {@link Network}:
 *
 * <ul>
 *   <li>Postgres 16 alpine — master-service's relational store (Flyway
 *       migrations run automatically on boot).</li>
 *   <li>Redis 7 alpine — shared by master-service (idempotency keys) and
 *       gateway-service (SCG rate-limit counters).</li>
 *   <li>Kafka (KRaft) — master-service publishes outbox events; optional
 *       consumer helper available for assertions.</li>
 *   <li>master-service — image resolved from system property
 *       {@code wms.e2e.masterImage} when set (CI pre-build path), otherwise
 *       built on-the-fly via {@link ImageFromDockerfile} (local dev path).</li>
 *   <li>gateway-service — same dual-path strategy via
 *       {@code wms.e2e.gatewayImage}; env vars point at the in-network master
 *       hostname, the shared Redis, and the host-local MockWebServer JWKS.</li>
 * </ul>
 *
 * <p>The MockWebServer (JWKS stand-in) lives in the JVM running the tests —
 * not inside the Docker network. The gateway reaches it via
 * {@code host.docker.internal:{port}}, enabled by
 * {@code withExtraHost("host.docker.internal", "host-gateway")}.
 *
 * <p>Annotated {@link org.testcontainers.junit.jupiter.Testcontainers} with
 * {@code disabledWithoutDocker = true} so CI runs on Linux pick this up and
 * local Windows runs without a working Docker daemon skip gracefully rather
 * than hang.
 */
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class E2EBase {

    protected static final String POSTGRES_IMAGE = "postgres:16-alpine";
    protected static final String REDIS_IMAGE = "redis:7-alpine";
    protected static final String KAFKA_IMAGE = "apache/kafka:3.7.0";

    protected static final String POSTGRES_ALIAS = "e2e-postgres";
    protected static final String REDIS_ALIAS = "e2e-redis";
    protected static final String KAFKA_ALIAS = "e2e-kafka";
    protected static final String MASTER_ALIAS = "e2e-master";
    protected static final String GATEWAY_ALIAS = "e2e-gateway";

    protected static final int GATEWAY_PORT = 8080;
    protected static final int MASTER_PORT = 8081;

    // Paths are relative to the wms-platform project root. locateFile() walks
    // up from the current working directory until it finds the prefix — this
    // works in both the monorepo (cwd deep under projects/wms-platform/) and
    // the extracted standalone repo (cwd deep under the repo root).
    /** Resolves absolute paths to the built boot jars; Gradle produces these first. */
    private static final Path MASTER_JAR = locateJar(
            "apps/master-service/build/libs/master-service.jar");
    private static final Path GATEWAY_JAR = locateJar(
            "apps/gateway-service/build/libs/gateway-service.jar");

    /** Dockerfile locations — reused verbatim from production image builds. */
    private static final Path MASTER_DOCKERFILE = locateFile(
            "apps/master-service/Dockerfile");
    private static final Path GATEWAY_DOCKERFILE = locateFile(
            "apps/gateway-service/Dockerfile");

    protected Network network;
    protected PostgreSQLContainer<?> postgres;
    protected GenericContainer<?> redis;
    protected KafkaContainer kafka;
    protected GenericContainer<?> master;
    protected GenericContainer<?> gateway;

    protected JwtTestHelper jwt;
    protected JwksMockServer jwks;

    @BeforeAll
    void startInfrastructure() throws Exception {
        network = Network.newNetwork();

        postgres = new PostgreSQLContainer<>(POSTGRES_IMAGE)
                .withDatabaseName("master_db")
                .withUsername("master")
                .withPassword("master")
                .withNetwork(network)
                .withNetworkAliases(POSTGRES_ALIAS);
        postgres.start();

        redis = new RedisContainer(DockerImageName.parse(REDIS_IMAGE))
                .withNetwork(network)
                .withNetworkAliases(REDIS_ALIAS);
        redis.start();

        kafka = new KafkaContainer(DockerImageName.parse(KAFKA_IMAGE))
                .withNetwork(network)
                .withNetworkAliases(KAFKA_ALIAS);
        kafka.waitingFor(Wait.forLogMessage(".*Kafka Server started.*", 1)
                .withStartupTimeout(Duration.ofMinutes(2)));
        kafka.start();

        // JWKS stand-in on the host JVM — gateway reaches it via
        // host.docker.internal. MUST start before the gateway container boots
        // because the gateway fetches JWKS eagerly at first request.
        jwt = new JwtTestHelper();
        jwks = new JwksMockServer(jwt);

        master = buildServiceContainer("wms.e2e.masterImage", MASTER_JAR, MASTER_DOCKERFILE, MASTER_PORT)
                .withNetwork(network)
                .withNetworkAliases(MASTER_ALIAS)
                .withExtraHost("host.docker.internal", "host-gateway")
                // `integration` profile exposes the `metrics` actuator endpoint
                // so masterRequestCount() (used by scenario 2) can query
                // http.server.requests. Production stays locked down.
                .withEnv("SPRING_PROFILES_ACTIVE", "integration")
                .withEnv("DB_URL", "jdbc:postgresql://" + POSTGRES_ALIAS + ":5432/master_db")
                .withEnv("DB_USERNAME", "master")
                .withEnv("DB_PASSWORD", "master")
                .withEnv("REDIS_HOST", REDIS_ALIAS)
                .withEnv("REDIS_PORT", "6379")
                // Peer-container bootstrap port: Testcontainers' KafkaContainer
                // (apache/kafka image, KRaft mode) exposes PLAINTEXT on 9092.
                // Port 9093 is the internal BROKER listener reserved for
                // inter-broker traffic — wiring a client to it silently fails
                // to consume advertised listeners.
                .withEnv("KAFKA_BOOTSTRAP_SERVERS", KAFKA_ALIAS + ":9092")
                .withEnv("JWT_JWKS_URI", jwks.containerJwksUrl())
                .withEnv("SERVER_PORT", String.valueOf(MASTER_PORT))
                .waitingFor(Wait.forHttp("/actuator/health")
                        .forStatusCode(200)
                        .withStartupTimeout(Duration.ofMinutes(3)));
        master.start();

        gateway = buildServiceContainer("wms.e2e.gatewayImage", GATEWAY_JAR, GATEWAY_DOCKERFILE, GATEWAY_PORT)
                .withNetwork(network)
                .withNetworkAliases(GATEWAY_ALIAS)
                .withExtraHost("host.docker.internal", "host-gateway")
                .withEnv("SPRING_PROFILES_ACTIVE", "default")
                .withEnv("REDIS_HOST", REDIS_ALIAS)
                .withEnv("REDIS_PORT", "6379")
                .withEnv("MASTER_SERVICE_URI", "http://" + MASTER_ALIAS + ":" + MASTER_PORT)
                .withEnv("JWT_JWKS_URI", jwks.containerJwksUrl())
                .withEnv("CORS_ALLOWED_ORIGINS", "http://localhost:3000")
                .withEnv("SERVER_PORT", String.valueOf(GATEWAY_PORT))
                .waitingFor(Wait.forHttp("/actuator/health")
                        .forStatusCode(200)
                        .withStartupTimeout(Duration.ofMinutes(3)));
        gateway.start();
    }

    @AfterAll
    void stopInfrastructure() throws Exception {
        if (jwks != null) {
            jwks.close();
        }
        if (gateway != null) {
            gateway.stop();
        }
        if (master != null) {
            master.stop();
        }
        if (kafka != null) {
            kafka.stop();
        }
        if (redis != null) {
            redis.stop();
        }
        if (postgres != null) {
            postgres.stop();
        }
        if (network != null) {
            network.close();
        }
    }

    /**
     * Builds the container for a service.
     *
     * <p>When {@code prebuiltImageProp} is set as a system property (CI path),
     * skips {@link ImageFromDockerfile} entirely and uses the pre-built image
     * name directly. This avoids the Docker 28 BuildKit gRPC hang that occurs
     * when Testcontainers' Docker Java client calls the REST {@code /build}
     * endpoint. On Docker 28 the REST path always routes through BuildKit and
     * hangs indefinitely; the CLI-built image sidesteps the issue.
     *
     * <p>When the property is absent (local dev path), falls back to
     * {@link ImageFromDockerfile} so developers without a pre-built image can
     * still run the suite with a plain {@code ./gradlew e2eTest}.
     */
    private static GenericContainer<?> buildServiceContainer(
            String prebuiltImageProp, Path jar, Path dockerfile, int exposedPort) {
        String prebuiltImage = System.getProperty(prebuiltImageProp);
        if (prebuiltImage != null && !prebuiltImage.isBlank()) {
            return new GenericContainer<>(DockerImageName.parse(prebuiltImage))
                    .withExposedPorts(exposedPort);
        }
        ImageFromDockerfile image = new ImageFromDockerfile()
                .withDockerfile(dockerfile)
                .withFileFromPath("build/libs/" + jar.getFileName().toString(), jar)
                .withFileFromPath("Dockerfile", dockerfile);
        return new GenericContainer<>(image).withExposedPorts(exposedPort);
    }

    /** Gateway base URL reachable from the host JVM (for HTTP client calls). */
    protected URI gatewayBaseUri() {
        return URI.create("http://" + gateway.getHost() + ":" + gateway.getMappedPort(GATEWAY_PORT));
    }

    /** Master base URL reachable from the host JVM (for sanity / metrics checks). */
    protected URI masterBaseUri() {
        return URI.create("http://" + master.getHost() + ":" + master.getMappedPort(MASTER_PORT));
    }

    /**
     * Kafka bootstrap servers reachable from the host JVM (the test process).
     * Peer containers — including master-service — use {@code KAFKA_ALIAS:9092}
     * instead, see the {@code KAFKA_BOOTSTRAP_SERVERS} env on the master
     * container above.
     */
    protected String kafkaBootstrapForHost() {
        return kafka.getBootstrapServers();
    }

    private static Path locateJar(String relative) {
        Path p = locateFile(relative);
        if (!java.nio.file.Files.exists(p)) {
            throw new IllegalStateException(
                    "Expected boot jar not found at " + p
                            + " — ensure the bootJar task ran before e2eTest.");
        }
        return p;
    }

    /** Walks up from the working dir to find the monorepo root containing the file. */
    private static Path locateFile(String relative) {
        Path cwd = Paths.get("").toAbsolutePath();
        Path cur = cwd;
        for (int i = 0; i < 8 && cur != null; i++) {
            Path candidate = cur.resolve(relative);
            if (java.nio.file.Files.exists(candidate)) {
                return candidate.normalize();
            }
            cur = cur.getParent();
        }
        // Fall back to the naive resolve — the subsequent existence check will
        // report a clear error.
        return cwd.resolve(relative).normalize();
    }

}
