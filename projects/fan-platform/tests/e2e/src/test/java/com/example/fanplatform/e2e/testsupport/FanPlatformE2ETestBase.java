package com.example.fanplatform.e2e.testsupport;

import com.redis.testcontainers.RedisContainer;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base infrastructure for fan-platform v1 live-trio e2e tests
 * (TASK-FAN-INT-001).
 *
 * <p>Boots onto a shared {@link Network}:
 *
 * <ul>
 *   <li>Postgres 16 alpine — initialises {@code fanplatform_community} and
 *       {@code fanplatform_artist} databases via an embedded init script
 *       (mirrors the docker-compose setup at
 *       {@code projects/fan-platform/infra/postgres/init/01-create-databases.sh}).</li>
 *   <li>Redis 7 alpine — feed cache (community) + rate-limit counters (gateway).</li>
 *   <li>Kafka (KRaft) — community-service + artist-service publish outbox events.</li>
 *   <li>community-service — image resolved from system property
 *       {@code fan.e2e.communityImage} when set (CI pre-build path), otherwise
 *       built on-the-fly via {@link ImageFromDockerfile} (local dev path).</li>
 *   <li>artist-service — same dual-path strategy via {@code fan.e2e.artistImage}.</li>
 *   <li>gateway-service — same dual-path strategy via {@code fan.e2e.gatewayImage}.
 *       Extends the production gateway image with test-only env overrides for
 *       OIDC + downstream URLs (OIDC_ISSUER_URL, JWT_JWKS_URI, COMMUNITY_SERVICE_URI,
 *       ARTIST_SERVICE_URI, REDIS_HOST). RewritePath filters are baked into the
 *       production {@code application.yml} (TASK-FAN-BE-005).</li>
 * </ul>
 *
 * <p>The JWKS stand-in lives in the JVM running the tests — not inside the
 * Docker network. Each service container reaches it via
 * {@code host.docker.internal:{port}}, enabled by
 * {@code withExtraHost("host.docker.internal", "host-gateway")}.
 *
 * <p>Annotated {@link Testcontainers} with {@code disabledWithoutDocker = true}
 * so CI Linux runs pick this up and Windows dev machines without Docker skip
 * gracefully (per TASK-FAN-INT-001 § Failure Scenarios).
 */
@Tag("e2e")
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(FanPlatformE2ETestBase.ServiceContainerLogDumper.class)
public abstract class FanPlatformE2ETestBase {

    protected static final Logger log = LoggerFactory.getLogger(FanPlatformE2ETestBase.class);

    protected static final String POSTGRES_IMAGE = "postgres:16-alpine";
    protected static final String REDIS_IMAGE = "redis:7-alpine";
    protected static final String KAFKA_IMAGE = "apache/kafka:3.7.0";

    protected static final String POSTGRES_ALIAS = "fan-e2e-postgres";
    protected static final String REDIS_ALIAS = "fan-e2e-redis";
    protected static final String KAFKA_ALIAS = "fan-e2e-kafka";
    protected static final String COMMUNITY_ALIAS = "fan-e2e-community";
    protected static final String ARTIST_ALIAS = "fan-e2e-artist";
    protected static final String GATEWAY_ALIAS = "fan-e2e-gateway";

    protected static final int SERVICE_PORT = 8080;

    /** Internal Kafka listener port reachable inside the docker network. */
    private static final int KAFKA_INTERNAL_PORT = 9095;

    private static final String DB_USERNAME = "fanplatform";
    private static final String DB_PASSWORD = "fanplatform";
    private static final String DB_NAME_COMMUNITY = "fanplatform_community";
    private static final String DB_NAME_ARTIST = "fanplatform_artist";

    /** Boot jars produced by Gradle's {@code bootJar} task — referenced by the dev fallback path. */
    private static final Path GATEWAY_JAR = locateOptionalJar(
            "apps/gateway-service/build/libs/gateway-service.jar");
    private static final Path COMMUNITY_JAR = locateOptionalJar(
            "apps/community-service/build/libs/community-service.jar");
    private static final Path ARTIST_JAR = locateOptionalJar(
            "apps/artist-service/build/libs/artist-service.jar");

    /** Dockerfile locations — reused verbatim from production image builds. */
    private static final Path GATEWAY_DOCKERFILE = locateFile("apps/gateway-service/Dockerfile");
    private static final Path COMMUNITY_DOCKERFILE = locateFile("apps/community-service/Dockerfile");
    private static final Path ARTIST_DOCKERFILE = locateFile("apps/artist-service/Dockerfile");

    protected Network network;
    protected PostgreSQLContainer<?> postgres;
    protected GenericContainer<?> redis;
    protected KafkaContainer kafka;
    protected GenericContainer<?> community;
    protected GenericContainer<?> artist;
    protected GenericContainer<?> gateway;

    protected JwtTestHelper jwt;
    protected JwksMockServer jwks;

    protected final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @BeforeAll
    void startInfrastructure() throws Exception {
        // OpenAI Harness gap #3 Phase 3 (TASK-MONO-067) — when the
        // `-Pobservability=on` Gradle path injects the system property
        // `wms.e2e.observabilityNetwork`, reuse the named docker network
        // that scripts/observability/up.sh created. Property unset → behaviour
        // identical to the pre-Phase-3 path (anonymous Testcontainers
        // network). See:
        // docs/adr/ADR-MONO-007-worktree-ephemeral-observability-stack.md § 2.5 D5
        // tasks/done/TASK-MONO-066-observability-query-skill.md (gateway-service precedent)
        String observabilityNetwork = System.getProperty("wms.e2e.observabilityNetwork");
        if (observabilityNetwork != null && !observabilityNetwork.isBlank()) {
            String netName = observabilityNetwork;
            network = Network.builder()
                    .createNetworkCmdModifier(cmd -> cmd.withName(netName))
                    .build();
        } else {
            network = Network.newNetwork();
        }

        // ----- Postgres with multi-database init script ---------------------
        // Postgres image's docker-entrypoint runs *.sh / *.sql files in
        // /docker-entrypoint-initdb.d on first boot. Copy the project's init
        // script into the container so the two per-service databases exist
        // before community-service / artist-service start their Flyway migrations.
        postgres = new PostgreSQLContainer<>(POSTGRES_IMAGE)
                .withDatabaseName(DB_NAME_COMMUNITY) // creates the first DB; second DB created by init script
                .withUsername(DB_USERNAME)
                .withPassword(DB_PASSWORD)
                .withNetwork(network)
                .withNetworkAliases(POSTGRES_ALIAS)
                .withEnv("POSTGRES_DB_COMMUNITY", DB_NAME_COMMUNITY)
                .withEnv("POSTGRES_DB_ARTIST", DB_NAME_ARTIST)
                .withCopyFileToContainer(
                        org.testcontainers.utility.MountableFile.forHostPath(
                                locateFile("infra/postgres/init/01-create-databases.sh").toString()),
                        "/docker-entrypoint-initdb.d/01-create-databases.sh");
        postgres.start();

        // ----- Redis --------------------------------------------------------
        redis = new RedisContainer(DockerImageName.parse(REDIS_IMAGE))
                .withNetwork(network)
                .withNetworkAliases(REDIS_ALIAS);
        redis.start();

        // ----- Kafka (KRaft) ------------------------------------------------
        kafka = new KafkaContainer(DockerImageName.parse(KAFKA_IMAGE))
                .withNetwork(network)
                .withNetworkAliases(KAFKA_ALIAS)
                .withListener(KAFKA_ALIAS + ":" + KAFKA_INTERNAL_PORT);
        kafka.waitingFor(Wait.forLogMessage(".*Kafka Server started.*", 1)
                .withStartupTimeout(Duration.ofMinutes(2)));
        kafka.start();

        // ----- JWKS stand-in (host JVM, reachable via host.docker.internal) -
        // MUST start before any service that performs an eager JWKS fetch on
        // first request (gateway has a startup probe — disabled below via
        // GATEWAY_JWKS_STARTUP_PROBE_ENABLED=false to keep boot deterministic).
        jwt = new JwtTestHelper();
        jwks = new JwksMockServer(jwt);

        // ----- artist-service ----------------------------------------------
        artist = buildServiceContainer("fan.e2e.artistImage", ARTIST_JAR, ARTIST_DOCKERFILE)
                .withNetwork(network)
                .withNetworkAliases(ARTIST_ALIAS)
                .withExtraHost("host.docker.internal", "host-gateway")
                .withEnv("SERVER_PORT", String.valueOf(SERVICE_PORT))
                .withEnv("SPRING_PROFILES_ACTIVE", "default")
                .withEnv("POSTGRES_HOST", POSTGRES_ALIAS)
                .withEnv("POSTGRES_PORT", "5432")
                .withEnv("POSTGRES_DB_ARTIST", DB_NAME_ARTIST)
                .withEnv("POSTGRES_USER", DB_USERNAME)
                .withEnv("POSTGRES_PASSWORD", DB_PASSWORD)
                .withEnv("REDIS_HOST", REDIS_ALIAS)
                .withEnv("REDIS_PORT", "6379")
                .withEnv("KAFKA_BOOTSTRAP", KAFKA_ALIAS + ":" + KAFKA_INTERNAL_PORT)
                .withEnv("OIDC_ISSUER_URL", JwtTestHelper.SAS_ISSUER)
                .withEnv("JWT_JWKS_URI", jwks.containerJwksUrl())
                .withEnv("OIDC_REQUIRED_TENANT_ID", JwtTestHelper.DEFAULT_TENANT_ID)
                // Outbox: poll every 500 ms so the e2e Awaitility windows
                // (15-30 s) catch publishes promptly (TASK-FAN-INT-001 § Edge Cases).
                .withEnv("OUTBOX_POLLING_INTERVAL_MS", "500")
                .waitingFor(Wait.forHttp("/actuator/health")
                        .forStatusCode(200)
                        .withStartupTimeout(Duration.ofMinutes(3)));
        artist.start();

        // ----- community-service -------------------------------------------
        community = buildServiceContainer("fan.e2e.communityImage", COMMUNITY_JAR, COMMUNITY_DOCKERFILE)
                .withNetwork(network)
                .withNetworkAliases(COMMUNITY_ALIAS)
                .withExtraHost("host.docker.internal", "host-gateway")
                .withEnv("SERVER_PORT", String.valueOf(SERVICE_PORT))
                .withEnv("SPRING_PROFILES_ACTIVE", "default")
                .withEnv("POSTGRES_HOST", POSTGRES_ALIAS)
                .withEnv("POSTGRES_PORT", "5432")
                .withEnv("POSTGRES_DB_COMMUNITY", DB_NAME_COMMUNITY)
                .withEnv("POSTGRES_USER", DB_USERNAME)
                .withEnv("POSTGRES_PASSWORD", DB_PASSWORD)
                .withEnv("REDIS_HOST", REDIS_ALIAS)
                .withEnv("REDIS_PORT", "6379")
                .withEnv("KAFKA_BOOTSTRAP", KAFKA_ALIAS + ":" + KAFKA_INTERNAL_PORT)
                .withEnv("OIDC_ISSUER_URL", JwtTestHelper.SAS_ISSUER)
                .withEnv("JWT_JWKS_URI", jwks.containerJwksUrl())
                .withEnv("OIDC_REQUIRED_TENANT_ID", JwtTestHelper.DEFAULT_TENANT_ID)
                .withEnv("OUTBOX_POLLING_INTERVAL_MS", "500")
                // The live-trio is gateway+community+artist only — membership-service
                // and iam (the workload-identity token source) are out of scope, so
                // HttpMembershipChecker would fail-closed on every MEMBERS_ONLY/PREMIUM
                // read. Opt out via the documented escape hatch so community falls
                // back to AlwaysAllowMembershipChecker (v1 stub). The real HTTP gate
                // is covered by MembershipGateIntegrationTest + federation-hardening-e2e.
                .withEnv("COMMUNITY_MEMBERSHIP_SERVICE_ENABLED", "false")
                .waitingFor(Wait.forHttp("/actuator/health")
                        .forStatusCode(200)
                        .withStartupTimeout(Duration.ofMinutes(3)));
        community.start();

        // ----- gateway-service ---------------------------------------------
        gateway = buildServiceContainer("fan.e2e.gatewayImage", GATEWAY_JAR, GATEWAY_DOCKERFILE)
                .withNetwork(network)
                .withNetworkAliases(GATEWAY_ALIAS)
                .withExtraHost("host.docker.internal", "host-gateway")
                .withEnv("SERVER_PORT", String.valueOf(SERVICE_PORT))
                .withEnv("SPRING_PROFILES_ACTIVE", "default")
                .withEnv("REDIS_HOST", REDIS_ALIAS)
                .withEnv("REDIS_PORT", "6379")
                .withEnv("COMMUNITY_SERVICE_URI", "http://" + COMMUNITY_ALIAS + ":" + SERVICE_PORT)
                .withEnv("ARTIST_SERVICE_URI", "http://" + ARTIST_ALIAS + ":" + SERVICE_PORT)
                .withEnv("OIDC_ISSUER_URL", JwtTestHelper.SAS_ISSUER)
                .withEnv("JWT_JWKS_URI", jwks.containerJwksUrl())
                .withEnv("OIDC_REQUIRED_TENANT_ID", JwtTestHelper.DEFAULT_TENANT_ID)
                .withEnv("CORS_ALLOWED_ORIGINS", "http://fan-platform.local,http://localhost:3000")
                // Disable the JWKS startup probe — the JWKS server starts
                // before the gateway, but the probe's 30 s timeout would
                // still slow the boot signal on cold runners.
                .withEnv("GATEWAY_JWKS_STARTUP_PROBE_ENABLED", "false")
                .waitingFor(Wait.forHttp("/actuator/health")
                        .forStatusCode(200)
                        .withStartupTimeout(Duration.ofMinutes(3)));
        gateway.start();

        log.info("fan-platform e2e infrastructure ready: gateway={} community={} artist={} kafka={}",
                gatewayBaseUri(), community.getContainerId(), artist.getContainerId(),
                kafka.getBootstrapServers());
    }

    @AfterAll
    void stopInfrastructure() throws IOException {
        if (jwks != null) jwks.close();
        if (gateway != null) gateway.stop();
        if (community != null) community.stop();
        if (artist != null) artist.stop();
        if (kafka != null) kafka.stop();
        if (redis != null) redis.stop();
        if (postgres != null) postgres.stop();
        if (network != null) network.close();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Gateway URL reachable from the host JVM (HTTP client targets this). */
    protected URI gatewayBaseUri() {
        return URI.create("http://" + gateway.getHost() + ":" + gateway.getMappedPort(SERVICE_PORT));
    }

    /** Kafka bootstrap address reachable from the host JVM. Containers use the network alias instead. */
    protected String kafkaBootstrapForHost() {
        return kafka.getBootstrapServers();
    }

    /**
     * Builds the container for a service.
     *
     * <p>When {@code prebuiltImageProp} is set as a system property (CI path),
     * skips {@link ImageFromDockerfile} entirely and uses the pre-built image
     * name directly. This avoids the Docker 28 BuildKit gRPC hang documented
     * in {@code projects/wms-platform/apps/gateway-service/.../E2EBase.java}.
     *
     * <p>When the property is absent (local dev path), falls back to
     * {@link ImageFromDockerfile} so developers without a pre-built image can
     * still run the suite with a plain {@code ./gradlew :...:e2eTest}.
     */
    private static GenericContainer<?> buildServiceContainer(
            String prebuiltImageProp, Path jar, Path dockerfile) {
        String prebuiltImage = System.getProperty(prebuiltImageProp);
        if (prebuiltImage != null && !prebuiltImage.isBlank()) {
            return new GenericContainer<>(DockerImageName.parse(prebuiltImage))
                    .withExposedPorts(SERVICE_PORT);
        }
        if (jar == null) {
            throw new IllegalStateException(
                    "No pre-built image system property (" + prebuiltImageProp + ") and no boot jar"
                            + " on disk for fallback ImageFromDockerfile path. Either set the system"
                            + " property to a pre-built image (CI path) or run the corresponding"
                            + " bootJar task (local dev path).");
        }
        ImageFromDockerfile image = new ImageFromDockerfile()
                .withDockerfile(dockerfile)
                .withFileFromPath("build/libs/" + jar.getFileName().toString(), jar)
                .withFileFromPath("Dockerfile", dockerfile);
        return new GenericContainer<>(image).withExposedPorts(SERVICE_PORT);
    }

    private static Path locateOptionalJar(String relative) {
        Path p = locateFile(relative);
        return java.nio.file.Files.exists(p) ? p : null;
    }

    /**
     * Walks up from the working dir to find the fan-platform project root
     * containing the relative path. Works in both monorepo layout
     * (cwd deep under {@code projects/fan-platform/...}) and in the future
     * extracted-standalone layout (cwd deep under the extracted repo root).
     */
    private static Path locateFile(String relative) {
        Path cwd = Paths.get("").toAbsolutePath();
        Path cur = cwd;
        // Try a couple of project-relative roots: the fan-platform project
        // root and any of its ancestors. Eight levels is enough for both the
        // monorepo (5-6 deep) and standalone (1-2 deep) layouts.
        for (int i = 0; i < 8 && cur != null; i++) {
            Path candidate = cur.resolve(relative);
            if (java.nio.file.Files.exists(candidate)) {
                return candidate.normalize();
            }
            // Try the fan-platform project sub-path explicitly so resolution
            // works when Gradle invokes the e2e task from the monorepo root.
            Path projectScoped = cur.resolve("projects/fan-platform").resolve(relative);
            if (java.nio.file.Files.exists(projectScoped)) {
                return projectScoped.normalize();
            }
            cur = cur.getParent();
        }
        // Fall back to the naive resolve — the subsequent existence check or
        // ImageFromDockerfile call will report a clear error.
        return cwd.resolve(relative).normalize();
    }

    /**
     * Dumps each service container's stdout+stderr to {@code System.err} when an
     * e2e test fails, so the CI log carries the actual stack trace that produced
     * a 4xx/5xx response. Without this, GitHub Actions only shows the JUnit
     * assertion ("expected: 201 / but was: 500") with no service-side context.
     *
     * <p>Triggered automatically by JUnit 5 because every concrete e2e test class
     * extends this base class and inherits the {@code @ExtendWith} declaration.
     */
    public static class ServiceContainerLogDumper implements TestWatcher {

        @Override
        public void testFailed(ExtensionContext context, Throwable cause) {
            Object instance = context.getTestInstance().orElse(null);
            if (!(instance instanceof FanPlatformE2ETestBase suite)) {
                return;
            }
            System.err.println("================================================================");
            System.err.println("[e2e-fail] " + context.getDisplayName());
            System.err.println("[e2e-fail] dumping service container logs for diagnosis");
            System.err.println("================================================================");
            dumpContainerLogs("gateway", suite.gateway);
            dumpContainerLogs("community", suite.community);
            dumpContainerLogs("artist", suite.artist);
        }

        private static void dumpContainerLogs(String label, GenericContainer<?> container) {
            if (container == null || !container.isRunning()) {
                System.err.println("[e2e-fail] " + label + " container: <not running>");
                return;
            }
            try {
                String logs = container.getLogs();
                System.err.println("---- " + label + " container logs (" + container.getContainerId() + ") ----");
                System.err.println(logs);
                System.err.println("---- end " + label + " logs ----");
            } catch (Exception e) {
                System.err.println("[e2e-fail] " + label + " getLogs() failed: " + e.getMessage());
            }
        }
    }
}
