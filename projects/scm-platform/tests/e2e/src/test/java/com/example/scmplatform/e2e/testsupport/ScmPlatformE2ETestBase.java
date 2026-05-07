package com.example.scmplatform.e2e.testsupport;

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
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base infrastructure for scm-platform v1 cross-service e2e tests
 * (TASK-SCM-INT-001).
 *
 * <p>Boots onto a shared {@link Network}:
 *
 * <ul>
 *   <li>Postgres 16 alpine — initialises {@code scm_procurement} and
 *       {@code scm_inventory_visibility} databases via the project's existing
 *       init script ({@code projects/scm-platform/infra/postgres/init/
 *       01-create-databases.sh}).</li>
 *   <li>Redis 7 alpine — gateway rate-limit counters + visibility cache.</li>
 *   <li>Kafka (KRaft) — procurement-service outbox relay target +
 *       inventory-visibility-service consumer source. Cross-project events
 *       (wms.inventory.*) are emitted by {@link KafkaTestProducer} from the
 *       host JVM so this suite does not need to boot wms containers
 *       (Failure Scenario B in the task spec).</li>
 *   <li>procurement-service — Spring Boot service. Image resolved from system
 *       property {@code scm.e2e.procurementImage} when set (CI pre-build path),
 *       otherwise built via {@link ImageFromDockerfile} (local dev path).</li>
 *   <li>inventory-visibility-service — same dual-path strategy via
 *       {@code scm.e2e.inventoryVisibilityImage}.</li>
 *   <li>gateway-service — same dual-path via {@code scm.e2e.gatewayImage}.
 *       JWKS startup probe is disabled to keep boot deterministic on cold
 *       runners.</li>
 * </ul>
 *
 * <p>JWKS stand-in lives in the JVM running the tests (see
 * {@link JwksMockServer}). Each service container reaches it via
 * {@code host.docker.internal:{port}}, enabled by
 * {@code withExtraHost("host.docker.internal", "host-gateway")}.
 *
 * <p>Supplier mock ({@link SupplierMockServer}) lives in the host JVM as well
 * — only procurement-service hits it, and tests pre-arrange responses per
 * scenario.
 *
 * <p>Annotated {@link Testcontainers} with {@code disabledWithoutDocker = true}
 * so CI Linux runs pick this up and Windows dev machines without Docker skip
 * gracefully (per TASK-SCM-INT-001 § Failure Scenarios A).
 */
@Tag("e2e")
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(ScmPlatformE2ETestBase.ServiceContainerLogDumper.class)
public abstract class ScmPlatformE2ETestBase {

    protected static final Logger log = LoggerFactory.getLogger(ScmPlatformE2ETestBase.class);

    protected static final String POSTGRES_IMAGE = "postgres:16-alpine";
    protected static final String REDIS_IMAGE = "redis:7-alpine";
    protected static final String KAFKA_IMAGE = "apache/kafka:3.7.0";

    protected static final String POSTGRES_ALIAS = "scm-e2e-postgres";
    protected static final String REDIS_ALIAS = "scm-e2e-redis";
    protected static final String KAFKA_ALIAS = "scm-e2e-kafka";
    protected static final String PROCUREMENT_ALIAS = "scm-e2e-procurement";
    protected static final String INVENTORY_VISIBILITY_ALIAS = "scm-e2e-inventory-visibility";
    protected static final String GATEWAY_ALIAS = "scm-e2e-gateway";

    protected static final int SERVICE_PORT = 8080;

    /** Internal Kafka listener port reachable inside the docker network. */
    private static final int KAFKA_INTERNAL_PORT = 9095;

    private static final String DB_USERNAME = "scm";
    private static final String DB_PASSWORD = "scm";
    private static final String DB_NAME_PROCUREMENT = "scm_procurement";
    private static final String DB_NAME_INVENTORY_VISIBILITY = "scm_inventory_visibility";

    /** Boot jars produced by Gradle's {@code bootJar} task — referenced by the dev fallback path. */
    private static final Path GATEWAY_JAR = locateOptionalJar(
            "apps/gateway-service/build/libs/gateway-service.jar");
    private static final Path PROCUREMENT_JAR = locateOptionalJar(
            "apps/procurement-service/build/libs/procurement-service.jar");
    private static final Path INVENTORY_VISIBILITY_JAR = locateOptionalJar(
            "apps/inventory-visibility-service/build/libs/inventory-visibility-service.jar");

    /** Dockerfile locations — reused verbatim from production image builds. */
    private static final Path GATEWAY_DOCKERFILE = locateFile("apps/gateway-service/Dockerfile");
    private static final Path PROCUREMENT_DOCKERFILE = locateFile("apps/procurement-service/Dockerfile");
    private static final Path INVENTORY_VISIBILITY_DOCKERFILE =
            locateFile("apps/inventory-visibility-service/Dockerfile");

    protected Network network;
    protected PostgreSQLContainer<?> postgres;
    protected GenericContainer<?> redis;
    protected KafkaContainer kafka;
    protected GenericContainer<?> procurement;
    protected GenericContainer<?> inventoryVisibility;
    protected GenericContainer<?> gateway;

    protected JwtTestHelper jwt;
    protected JwksMockServer jwks;
    protected SupplierMockServer supplierMock;

    protected final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @BeforeAll
    void startInfrastructure() throws Exception {
        network = Network.newNetwork();

        // ----- Postgres with multi-database init script ---------------------
        // Use the built-in 'postgres' admin DB so PostgreSQLContainer's automatic
        // CREATE DATABASE step is a no-op; the init script then creates both
        // service DBs (scm_procurement + scm_inventory_visibility) atomically.
        // Pre-fix: withDatabaseName(DB_NAME_PROCUREMENT) caused entrypoint to
        // CREATE DATABASE scm_procurement, then the init script tried the same
        // CREATE → ON_ERROR_STOP=1 → exit 3 → wait-strategy timeout.
        postgres = new PostgreSQLContainer<>(POSTGRES_IMAGE)
                .withDatabaseName("postgres")
                .withUsername(DB_USERNAME)
                .withPassword(DB_PASSWORD)
                .withNetwork(network)
                .withNetworkAliases(POSTGRES_ALIAS)
                .withEnv("POSTGRES_DB_PROCUREMENT", DB_NAME_PROCUREMENT)
                .withEnv("POSTGRES_DB_INVENTORY_VISIBILITY", DB_NAME_INVENTORY_VISIBILITY)
                .withCopyFileToContainer(
                        org.testcontainers.utility.MountableFile.forHostPath(
                                locateFile("infra/postgres/init/01-create-databases.sh").toString()),
                        "/docker-entrypoint-initdb.d/01-create-databases.sh")
                .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("scm-e2e.postgres")));
        postgres.start();

        // ----- Redis --------------------------------------------------------
        redis = new RedisContainer(DockerImageName.parse(REDIS_IMAGE))
                .withNetwork(network)
                .withNetworkAliases(REDIS_ALIAS)
                .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("scm-e2e.redis")));
        redis.start();

        // ----- Kafka (KRaft) ------------------------------------------------
        kafka = new KafkaContainer(DockerImageName.parse(KAFKA_IMAGE))
                .withNetwork(network)
                .withNetworkAliases(KAFKA_ALIAS)
                .withListener(KAFKA_ALIAS + ":" + KAFKA_INTERNAL_PORT)
                .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("scm-e2e.kafka")));
        kafka.waitingFor(Wait.forLogMessage(".*Kafka Server started.*", 1)
                .withStartupTimeout(Duration.ofMinutes(2)));
        kafka.start();

        // ----- JWKS + supplier stand-ins (host JVM, reachable via host.docker.internal) -
        jwt = new JwtTestHelper();
        jwks = new JwksMockServer(jwt);
        supplierMock = new SupplierMockServer();

        // ----- procurement-service -----------------------------------------
        procurement = buildServiceContainer(
                "scm.e2e.procurementImage", PROCUREMENT_JAR, PROCUREMENT_DOCKERFILE)
                .withNetwork(network)
                .withNetworkAliases(PROCUREMENT_ALIAS)
                .withExtraHost("host.docker.internal", "host-gateway")
                .withEnv("SERVER_PORT", String.valueOf(SERVICE_PORT))
                .withEnv("SPRING_PROFILES_ACTIVE", "default")
                .withEnv("POSTGRES_HOST", POSTGRES_ALIAS)
                .withEnv("POSTGRES_PORT", "5432")
                .withEnv("POSTGRES_DB_PROCUREMENT", DB_NAME_PROCUREMENT)
                .withEnv("POSTGRES_USER", DB_USERNAME)
                .withEnv("POSTGRES_PASSWORD", DB_PASSWORD)
                .withEnv("REDIS_HOST", REDIS_ALIAS)
                .withEnv("REDIS_PORT", "6379")
                .withEnv("KAFKA_BOOTSTRAP", KAFKA_ALIAS + ":" + KAFKA_INTERNAL_PORT)
                .withEnv("OIDC_ISSUER_URL", JwtTestHelper.SAS_ISSUER)
                .withEnv("JWT_JWKS_URI", jwks.containerJwksUrl())
                .withEnv("OIDC_REQUIRED_TENANT_ID", JwtTestHelper.DEFAULT_TENANT_ID)
                // Supplier mock URL — host-side MockWebServer reachable via host.docker.internal.
                .withEnv("SUPPLIER_MOCK_BASE_URL", supplierMock.containerBaseUrl())
                // Outbox: poll every 500 ms so the e2e Awaitility windows
                // (15-30 s) catch publishes promptly (Edge Case #3).
                .withEnv("OUTBOX_POLLING_INTERVAL_MS", "500")
                .waitingFor(Wait.forHttp("/actuator/health")
                        .forStatusCode(200)
                        .withStartupTimeout(Duration.ofMinutes(3)))
                .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("scm-e2e.procurement")));
        procurement.start();

        // ----- inventory-visibility-service --------------------------------
        inventoryVisibility = buildServiceContainer(
                "scm.e2e.inventoryVisibilityImage", INVENTORY_VISIBILITY_JAR, INVENTORY_VISIBILITY_DOCKERFILE)
                .withNetwork(network)
                .withNetworkAliases(INVENTORY_VISIBILITY_ALIAS)
                .withExtraHost("host.docker.internal", "host-gateway")
                .withEnv("SERVER_PORT", String.valueOf(SERVICE_PORT))
                .withEnv("SPRING_PROFILES_ACTIVE", "default")
                .withEnv("POSTGRES_HOST", POSTGRES_ALIAS)
                .withEnv("POSTGRES_PORT", "5432")
                .withEnv("POSTGRES_DB", DB_NAME_INVENTORY_VISIBILITY)
                .withEnv("POSTGRES_USER", DB_USERNAME)
                .withEnv("POSTGRES_PASSWORD", DB_PASSWORD)
                .withEnv("REDIS_HOST", REDIS_ALIAS)
                .withEnv("REDIS_PORT", "6379")
                .withEnv("KAFKA_BOOTSTRAP", KAFKA_ALIAS + ":" + KAFKA_INTERNAL_PORT)
                .withEnv("OIDC_ISSUER_URL", JwtTestHelper.SAS_ISSUER)
                .withEnv("JWT_JWKS_URI", jwks.containerJwksUrl())
                .withEnv("OIDC_REQUIRED_TENANT_ID", JwtTestHelper.DEFAULT_TENANT_ID)
                .waitingFor(Wait.forHttp("/actuator/health")
                        .forStatusCode(200)
                        .withStartupTimeout(Duration.ofMinutes(3)))
                .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("scm-e2e.inventory-visibility")));
        inventoryVisibility.start();

        // ----- gateway-service ---------------------------------------------
        gateway = buildServiceContainer(
                "scm.e2e.gatewayImage", GATEWAY_JAR, GATEWAY_DOCKERFILE)
                .withNetwork(network)
                .withNetworkAliases(GATEWAY_ALIAS)
                .withExtraHost("host.docker.internal", "host-gateway")
                .withEnv("SERVER_PORT", String.valueOf(SERVICE_PORT))
                .withEnv("SPRING_PROFILES_ACTIVE", "default")
                .withEnv("REDIS_HOST", REDIS_ALIAS)
                .withEnv("REDIS_PORT", "6379")
                .withEnv("PROCUREMENT_SERVICE_URI", "http://" + PROCUREMENT_ALIAS + ":" + SERVICE_PORT)
                .withEnv("INVENTORY_VISIBILITY_SERVICE_URI",
                        "http://" + INVENTORY_VISIBILITY_ALIAS + ":" + SERVICE_PORT)
                .withEnv("OIDC_ISSUER_URL", JwtTestHelper.SAS_ISSUER)
                .withEnv("JWT_JWKS_URI", jwks.containerJwksUrl())
                .withEnv("OIDC_REQUIRED_TENANT_ID", JwtTestHelper.DEFAULT_TENANT_ID)
                .withEnv("CORS_ALLOWED_ORIGINS", "http://scm.local")
                // Disable the JWKS startup probe — the JWKS server starts
                // before the gateway, but the probe's 30 s timeout would
                // still slow the boot signal on cold runners.
                .withEnv("GATEWAY_JWKS_STARTUP_PROBE_ENABLED", "false")
                .waitingFor(Wait.forHttp("/actuator/health")
                        .forStatusCode(200)
                        .withStartupTimeout(Duration.ofMinutes(3)))
                .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("scm-e2e.gateway")));
        gateway.start();

        log.info("scm-platform e2e infrastructure ready: gateway={} procurement={} inventory-visibility={} kafka={}",
                gatewayBaseUri(), procurement.getContainerId(), inventoryVisibility.getContainerId(),
                kafka.getBootstrapServers());
    }

    @AfterAll
    void stopInfrastructure() throws IOException {
        if (supplierMock != null) supplierMock.close();
        if (jwks != null) jwks.close();
        if (gateway != null) gateway.stop();
        if (inventoryVisibility != null) inventoryVisibility.stop();
        if (procurement != null) procurement.stop();
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

    /**
     * Direct procurement-service URL — used by webhook scenarios that bypass
     * the gateway (the gateway only fronts {@code /api/v1/**}; webhooks land
     * on the service's {@code /api/procurement/webhooks/**} unauthenticated
     * path verified by shared-secret).
     */
    protected URI procurementBaseUri() {
        return URI.create("http://" + procurement.getHost() + ":" + procurement.getMappedPort(SERVICE_PORT));
    }

    /** Direct inventory-visibility URL — useful for actuator health verification only. */
    protected URI inventoryVisibilityBaseUri() {
        return URI.create("http://" + inventoryVisibility.getHost()
                + ":" + inventoryVisibility.getMappedPort(SERVICE_PORT));
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
     * name directly. Local dev (without the property) falls back to
     * {@link ImageFromDockerfile} so developers can run the suite without
     * a manual {@code docker build} step.
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
     * Walks up from the working dir to find the scm-platform project root
     * containing the relative path. Works in both monorepo layout
     * (cwd deep under {@code projects/scm-platform/...}) and the future
     * extracted-standalone layout (cwd deep under the extracted repo root).
     */
    private static Path locateFile(String relative) {
        Path cwd = Paths.get("").toAbsolutePath();
        Path cur = cwd;
        for (int i = 0; i < 8 && cur != null; i++) {
            Path candidate = cur.resolve(relative);
            if (java.nio.file.Files.exists(candidate)) {
                return candidate.normalize();
            }
            // Try the scm-platform project sub-path explicitly so resolution
            // works when Gradle invokes the e2e task from the monorepo root.
            Path projectScoped = cur.resolve("projects/scm-platform").resolve(relative);
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
     * a 4xx/5xx response. Adapted from the fan-platform e2e log dumper.
     */
    public static class ServiceContainerLogDumper implements TestWatcher {

        @Override
        public void testFailed(ExtensionContext context, Throwable cause) {
            Object instance = context.getTestInstance().orElse(null);
            if (!(instance instanceof ScmPlatformE2ETestBase suite)) {
                return;
            }
            System.err.println("================================================================");
            System.err.println("[e2e-fail] " + context.getDisplayName());
            System.err.println("[e2e-fail] dumping service container logs for diagnosis");
            System.err.println("================================================================");
            dumpContainerLogs("gateway", suite.gateway);
            dumpContainerLogs("procurement", suite.procurement);
            dumpContainerLogs("inventory-visibility", suite.inventoryVisibility);
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
