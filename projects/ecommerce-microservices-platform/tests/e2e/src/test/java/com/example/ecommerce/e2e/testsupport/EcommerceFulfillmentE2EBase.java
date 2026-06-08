package com.example.ecommerce.e2e.testsupport;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
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
import org.testcontainers.utility.MountableFile;

/**
 * Base infrastructure for the ecommerce cross-project fulfillment loop e2e
 * (TASK-MONO-195, ADR-MONO-022 §D7).
 *
 * <p>Boots onto a shared {@link Network}:
 *
 * <ul>
 *   <li>Postgres 16 alpine — one container, two databases ({@code order_db} +
 *       {@code shipping_db}) created by {@code init-databases.sql}. Each service
 *       runs its own Flyway migrations against its DB on boot.</li>
 *   <li>Kafka (KRaft) — the single shared broker carrying all five loop topics.
 *       The wms boundary event ({@code wms.outbound.shipping.confirmed.v1}) is
 *       host-synthesised onto this same broker per the monorepo cross-project
 *       idiom; the wms stack is intentionally NOT booted (its internal saga is
 *       gated by {@code FulfillmentRequestedConsumerIT}).</li>
 *   <li>order-service — real ecommerce service (default profile, so the
 *       {@code @Profile("!standalone")} StockChanged + ShippingStatusChanged
 *       consumers are active). Image from {@code ecommerce.e2e.orderImage}
 *       (CI) else built via {@link ImageFromDockerfile} from the bootJar.</li>
 *   <li>shipping-service — same dual-path via {@code ecommerce.e2e.shippingImage}.</li>
 * </ul>
 *
 * <p>Neither service requires OIDC/JWT or Redis (verified: REST is
 * {@code X-User-Id}-header based, consumers are Kafka-only) — so no JWKS stand-in
 * is needed, unlike the scm/iam/fan/wms gateway suites.
 *
 * <p>{@link Testcontainers} with {@code disabledWithoutDocker = true}: CI Linux
 * runs the suite; a dev machine without Docker skips gracefully (ADR-MONO-022
 * §D8 graceful degradation).
 */
@Tag("e2e")
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(EcommerceFulfillmentE2EBase.ServiceContainerLogDumper.class)
public abstract class EcommerceFulfillmentE2EBase {

    protected static final Logger log = LoggerFactory.getLogger(EcommerceFulfillmentE2EBase.class);

    protected static final String POSTGRES_IMAGE = "postgres:16-alpine";
    protected static final String KAFKA_IMAGE = "apache/kafka:3.7.0";

    protected static final String POSTGRES_ALIAS = "ecom-e2e-postgres";
    protected static final String KAFKA_ALIAS = "ecom-e2e-kafka";
    protected static final String ORDER_ALIAS = "ecom-e2e-order";
    protected static final String SHIPPING_ALIAS = "ecom-e2e-shipping";

    private static final int KAFKA_INTERNAL_PORT = 9095;
    private static final int ORDER_PORT = 8086;
    private static final int SHIPPING_PORT = 8090;

    private static final String DB_USER = "e2e";
    private static final String DB_PASSWORD = "e2e";

    // Loop topics (pre-created so consumers that subscribe on boot don't race the
    // first publish — the wms-IT / scm-e2e topic-metadata-race lesson).
    protected static final String TOPIC_STOCK_CHANGED = "product.product.stock-changed";
    protected static final String TOPIC_ORDER_CONFIRMED = "order.order.confirmed";
    protected static final String TOPIC_FULFILLMENT_REQUESTED = "ecommerce.fulfillment.requested.v1";
    protected static final String TOPIC_WMS_SHIPPING_CONFIRMED = "wms.outbound.shipping.confirmed.v1";
    protected static final String TOPIC_SHIPPING_STATUS_CHANGED = "shipping.shipping.status-changed";

    private static final Path ORDER_JAR = locateOptionalJar(
            "apps/order-service/build/libs");
    private static final Path SHIPPING_JAR = locateOptionalJar(
            "apps/shipping-service/build/libs");

    protected Network network;
    protected PostgreSQLContainer<?> postgres;
    protected KafkaContainer kafka;
    protected GenericContainer<?> order;
    protected GenericContainer<?> shipping;

    protected final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @BeforeAll
    void startInfrastructure() throws Exception {
        network = Network.newNetwork();

        // ----- Postgres (one container, two DBs via init script) ------------
        postgres = new PostgreSQLContainer<>(POSTGRES_IMAGE)
                .withDatabaseName("postgres")
                .withUsername(DB_USER)
                .withPassword(DB_PASSWORD)
                .withNetwork(network)
                .withNetworkAliases(POSTGRES_ALIAS)
                .withCopyFileToContainer(
                        MountableFile.forClasspathResource("init-databases.sql"),
                        "/docker-entrypoint-initdb.d/01-init-databases.sql")
                .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("ecom-e2e.postgres")));
        postgres.start();

        // ----- Kafka (KRaft) ------------------------------------------------
        kafka = new KafkaContainer(DockerImageName.parse(KAFKA_IMAGE))
                .withNetwork(network)
                .withNetworkAliases(KAFKA_ALIAS)
                .withListener(KAFKA_ALIAS + ":" + KAFKA_INTERNAL_PORT)
                .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("ecom-e2e.kafka")));
        kafka.waitingFor(Wait.forLogMessage(".*Kafka Server started.*", 1)
                .withStartupTimeout(Duration.ofMinutes(2)));
        kafka.start();

        preCreateTopics();

        String kafkaForContainers = KAFKA_ALIAS + ":" + KAFKA_INTERNAL_PORT;

        // ----- order-service (default profile → consumers active) -----------
        order = buildServiceContainer("ecommerce.e2e.orderImage", ORDER_JAR, ORDER_PORT)
                .withNetwork(network)
                .withNetworkAliases(ORDER_ALIAS)
                .withEnv("DB_URL", "jdbc:postgresql://" + POSTGRES_ALIAS + ":5432/order_db")
                .withEnv("DB_USERNAME", DB_USER)
                .withEnv("DB_PASSWORD", DB_PASSWORD)
                .withEnv("KAFKA_BOOTSTRAP_SERVERS", kafkaForContainers)
                .withEnv("OUTBOX_POLLING_INTERVAL_MS", "500")
                // Stuck-detector is irrelevant to the happy path; disable to keep logs quiet.
                .withEnv("ORDER_STUCK_DETECTOR_ENABLED", "false")
                .waitingFor(Wait.forHttp("/actuator/health").forStatusCode(200)
                        .withStartupTimeout(Duration.ofMinutes(3)))
                .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("ecom-e2e.order")));
        order.start();

        // ----- shipping-service ---------------------------------------------
        shipping = buildServiceContainer("ecommerce.e2e.shippingImage", SHIPPING_JAR, SHIPPING_PORT)
                .withNetwork(network)
                .withNetworkAliases(SHIPPING_ALIAS)
                .withEnv("DB_URL", "jdbc:postgresql://" + POSTGRES_ALIAS + ":5432/shipping_db")
                .withEnv("DB_USERNAME", DB_USER)
                .withEnv("DB_PASSWORD", DB_PASSWORD)
                .withEnv("KAFKA_BOOTSTRAP_SERVERS", kafkaForContainers)
                .withEnv("OUTBOX_POLLING_INTERVAL_MS", "500")
                .withEnv("FULFILLMENT_ENABLED", "true")
                .withEnv("FULFILLMENT_DEFAULT_WAREHOUSE_CODE", "WH-MAIN")
                .waitingFor(Wait.forHttp("/actuator/health").forStatusCode(200)
                        .withStartupTimeout(Duration.ofMinutes(3)))
                .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("ecom-e2e.shipping")));
        shipping.start();

        log.info("ecommerce fulfillment e2e ready: order={} shipping={} kafka={}",
                orderBaseUri(), shippingBaseUri(), kafka.getBootstrapServers());
    }

    @AfterAll
    void stopInfrastructure() {
        if (shipping != null) shipping.stop();
        if (order != null) order.stop();
        if (kafka != null) kafka.stop();
        if (postgres != null) postgres.stop();
        if (network != null) network.close();
    }

    private void preCreateTopics() throws Exception {
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        try (AdminClient admin = AdminClient.create(props)) {
            admin.createTopics(List.of(
                    new NewTopic(TOPIC_STOCK_CHANGED, 1, (short) 1),
                    new NewTopic(TOPIC_ORDER_CONFIRMED, 1, (short) 1),
                    new NewTopic(TOPIC_FULFILLMENT_REQUESTED, 1, (short) 1),
                    new NewTopic(TOPIC_WMS_SHIPPING_CONFIRMED, 1, (short) 1),
                    new NewTopic(TOPIC_SHIPPING_STATUS_CHANGED, 1, (short) 1)
            )).all().get(30, java.util.concurrent.TimeUnit.SECONDS);
            log.info("Pre-created {} loop topics", 5);
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    protected URI orderBaseUri() {
        return URI.create("http://" + order.getHost() + ":" + order.getMappedPort(ORDER_PORT));
    }

    protected URI shippingBaseUri() {
        return URI.create("http://" + shipping.getHost() + ":" + shipping.getMappedPort(SHIPPING_PORT));
    }

    /** Kafka bootstrap reachable from the host JVM. */
    protected String kafkaBootstrapForHost() {
        return kafka.getBootstrapServers();
    }

    /**
     * Builds a service container. When {@code prebuiltImageProp} is set as a
     * system property (CI path) the pre-built image is used directly; otherwise a
     * minimal image is assembled from the service bootJar via
     * {@link ImageFromDockerfile} (local dev). The minimal image deliberately
     * drops the production OTel javaagent — the e2e exercises the event loop, not
     * tracing — which also avoids the production Dockerfile's network download.
     */
    private static GenericContainer<?> buildServiceContainer(
            String prebuiltImageProp, Path jar, int port) {
        String prebuilt = System.getProperty(prebuiltImageProp);
        if (prebuilt != null && !prebuilt.isBlank()) {
            return new GenericContainer<>(DockerImageName.parse(prebuilt)).withExposedPorts(port);
        }
        if (jar == null) {
            throw new IllegalStateException(
                    "No pre-built image system property (" + prebuiltImageProp + ") and no bootJar on"
                            + " disk for the ImageFromDockerfile fallback. Either set the property to a"
                            + " pre-built image (CI path) or run the corresponding :bootJar (local dev).");
        }
        ImageFromDockerfile image = new ImageFromDockerfile()
                .withFileFromPath("app.jar", jar)
                .withDockerfileFromBuilder(b -> b
                        .from("eclipse-temurin:21-jre-alpine")
                        .copy("app.jar", "/app/app.jar")
                        .expose(port)
                        .entryPoint("java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar")
                        .build());
        return new GenericContainer<>(image).withExposedPorts(port);
    }

    /**
     * Locates the Spring Boot bootJar inside the given {@code build/libs}
     * directory (the executable jar, excluding the {@code -plain.jar} library
     * artifact). Walks up from the working dir so resolution works whether Gradle
     * invokes the task from the monorepo root or the ecommerce project root.
     * Returns {@code null} when no jar exists (CI prebuilt-image path).
     */
    private static Path locateOptionalJar(String relativeLibsDir) {
        Path libs = locateDir(relativeLibsDir);
        if (libs == null || !java.nio.file.Files.isDirectory(libs)) {
            return null;
        }
        try (Stream<Path> entries = java.nio.file.Files.list(libs)) {
            return entries
                    .filter(p -> p.getFileName().toString().endsWith(".jar"))
                    .filter(p -> !p.getFileName().toString().endsWith("-plain.jar"))
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    private static Path locateDir(String relative) {
        Path cur = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 8 && cur != null; i++) {
            Path candidate = cur.resolve(relative);
            if (java.nio.file.Files.exists(candidate)) {
                return candidate.normalize();
            }
            Path projectScoped = cur.resolve("projects/ecommerce-microservices-platform").resolve(relative);
            if (java.nio.file.Files.exists(projectScoped)) {
                return projectScoped.normalize();
            }
            cur = cur.getParent();
        }
        return null;
    }

    /**
     * Dumps both service containers' logs to {@code System.err} on test failure so
     * the CI log carries the stack trace behind a 4xx/5xx or a stuck loop.
     */
    public static class ServiceContainerLogDumper implements TestWatcher {
        @Override
        public void testFailed(ExtensionContext context, Throwable cause) {
            Object instance = context.getTestInstance().orElse(null);
            if (!(instance instanceof EcommerceFulfillmentE2EBase suite)) {
                return;
            }
            System.err.println("================================================================");
            System.err.println("[e2e-fail] " + context.getDisplayName());
            System.err.println("================================================================");
            dump("order", suite.order);
            dump("shipping", suite.shipping);
        }

        private static void dump(String label, GenericContainer<?> c) {
            if (c == null || !c.isRunning()) {
                System.err.println("[e2e-fail] " + label + " container: <not running>");
                return;
            }
            try {
                System.err.println("---- " + label + " logs (" + c.getContainerId() + ") ----");
                System.err.println(c.getLogs());
                System.err.println("---- end " + label + " logs ----");
            } catch (Exception e) {
                System.err.println("[e2e-fail] " + label + " getLogs() failed: " + e.getMessage());
            }
        }
    }
}
