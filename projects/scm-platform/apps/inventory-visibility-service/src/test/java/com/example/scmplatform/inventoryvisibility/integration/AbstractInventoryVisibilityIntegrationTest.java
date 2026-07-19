package com.example.scmplatform.inventoryvisibility.integration;

import com.example.scmplatform.inventoryvisibility.adapter.outbound.persistence.jpa.EventDedupeJpaRepository;
import com.example.scmplatform.inventoryvisibility.adapter.outbound.persistence.jpa.InventoryNodeJpaEntity;
import com.example.scmplatform.inventoryvisibility.adapter.outbound.persistence.jpa.InventoryNodeJpaRepository;
import com.example.scmplatform.inventoryvisibility.adapter.outbound.persistence.jpa.InventorySnapshotJpaRepository;
import com.example.scmplatform.inventoryvisibility.adapter.outbound.persistence.jpa.NodeStalenessJpaEntity;
import com.example.scmplatform.inventoryvisibility.adapter.outbound.persistence.jpa.NodeStalenessJpaRepository;
import com.example.testsupport.integration.DockerAvailableCondition;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

/**
 * Base class for inventory-visibility-service integration tests.
 *
 * <p>Pattern mirrors {@code AbstractProcurementIntegrationTest} (TASK-SCM-BE-002d):
 * shared Postgres + Kafka KRaft + Redis containers (started once per JVM via
 * static block), per-class context dirtied so consumer groups don't leak.
 *
 * <p>Test profile {@code application-test.yml} configures H2 + flyway-disabled
 * for unit slices; this base re-overrides datasource and flyway via
 * {@link DynamicPropertySource} so production {@code db/migration/inventory-visibility}
 * runs against Postgres exactly like the prod schema.
 *
 * <p>Regression guard #2 (TASK-SCM-INT-001b root cause #2 — Hibernate JSONB
 * mapping): subclass tests must drive the {@code InventoryNode} auto-create
 * path with a {@code null} {@code contact_info}, so removing
 * {@code @JdbcTypeCode(SqlTypes.JSON)} from {@code InventoryNodeJpaEntity}
 * causes those tests to fail with PostgreSQL 42804.
 */
@Tag("integration")
@ExtendWith(DockerAvailableCondition.class)
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class AbstractInventoryVisibilityIntegrationTest {

    protected static final String TENANT_SCM = "scm";
    protected static final String TENANT_OTHER = "tenant-other";

    static final String TOPIC_INVENTORY_RECEIVED = "wms.inventory.received.v1";
    static final String TOPIC_INVENTORY_ADJUSTED = "wms.inventory.adjusted.v1";
    static final String TOPIC_INVENTORY_TRANSFERRED = "wms.inventory.transferred.v1";
    protected static final String TOPIC_ALERT = "scm.inventory.alert.v1";

    @SuppressWarnings("resource")
    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("scm_inventory_visibility")
                    .withUsername("scm")
                    .withPassword("scm")
                    .withStartupTimeout(Duration.ofMinutes(3));

    // KRaft mode (cp-kafka 7.4+) emits `[RaftManager id=N] Completed transition to Leader(...)`
    // instead of the legacy `[KafkaServer id=N] started` line, so the old log-message
    // waiting strategy times out. ConfluentKafkaContainer ships its own internal wait
    // strategy that handles KRaft-mode startup correctly — defer to it.
    protected static final ConfluentKafkaContainer KAFKA =
            new ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"))
                    .withStartupTimeout(Duration.ofMinutes(3));

    @SuppressWarnings("resource")
    protected static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    static {
        POSTGRES.start();
        KAFKA.start();
        REDIS.start();
        preCreateTopics();
    }

    /**
     * Pre-create the wms.inventory.* topics that the service's {@code @KafkaListener}
     * subscribes to, before any context bootstrap.  Mirrors the
     * {@link AdminClient#createTopics(java.util.Collection)} pattern adopted in
     * TASK-SCM-INT-001b cycle 1 to avoid the
     * {@code UNKNOWN_TOPIC_OR_PARTITION} race when the consumer joins before
     * Kafka auto-creates the topic.
     */
    private static void preCreateTopics() {
        Properties props = new Properties();
        props.put("bootstrap.servers", KAFKA.getBootstrapServers());
        try (AdminClient admin = AdminClient.create(props)) {
            admin.createTopics(List.of(
                    new NewTopic(TOPIC_INVENTORY_RECEIVED, 1, (short) 1),
                    new NewTopic(TOPIC_INVENTORY_ADJUSTED, 1, (short) 1),
                    new NewTopic(TOPIC_INVENTORY_TRANSFERRED, 1, (short) 1),
                    new NewTopic(TOPIC_ALERT, 1, (short) 1)
            )).all().get(30, java.util.concurrent.TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            // Idempotent — topic already exists is fine
            if (e.getCause() != null
                    && e.getCause().getClass().getSimpleName().equals("TopicExistsException")) {
                return;
            }
            throw new IllegalStateException("Failed to pre-create Kafka topics", e);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to pre-create Kafka topics", e);
        }
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");

        // Override application-test.yml H2 settings — IT must run against the
        // production-shaped Postgres schema produced by Flyway migrations.
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.database-platform",
                () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.properties.hibernate.dialect",
                () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations",
                () -> "classpath:db/migration/inventory-visibility");

        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "");

        // Bypass JWKS fetch — these IT tests call the application service
        // directly or use Kafka rather than HTTP, so JWT validation is moot.
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> "http://localhost:9999/oauth2/jwks");
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                () -> "http://test-issuer");
        registry.add("scmplatform.oauth2.allowed-issuers", () -> "http://test-issuer");

        // Disable production scheduler triggers — tests invoke detection
        // directly via the application service to avoid timing flakes.
        registry.add("inventory-visibility.staleness.initial-delay-ms", () -> "3600000");
        registry.add("inventory-visibility.staleness.check-interval-ms", () -> "3600000");
    }

    @Autowired
    protected InventoryNodeJpaRepository nodeJpa;

    @Autowired
    protected InventorySnapshotJpaRepository snapshotJpa;

    @Autowired
    protected NodeStalenessJpaRepository stalenessJpa;

    @Autowired
    protected EventDedupeJpaRepository dedupeJpa;

    @Autowired
    protected ObjectMapper objectMapper;

    /**
     * Build the wms-platform global event envelope JSON for an
     * {@code inventory.adjusted.v1} event.
     */
    protected String adjustedEnvelope(UUID eventId, Instant occurredAt,
                                      String locationId, String skuId, long delta) {
        return adjustedEnvelope(eventId, occurredAt, locationId, skuId, delta, null);
    }

    /**
     * As {@link #adjustedEnvelope(UUID, Instant, String, String, long)}, with the additive
     * {@code warehouseCode} (ADR-MONO-050 D9 / TASK-SCM-BE-037). Pass {@code null} to omit
     * the field entirely — that is exactly what an older wms producer emits, so the
     * consumer must tolerate its absence.
     */
    protected String adjustedEnvelope(UUID eventId, Instant occurredAt,
                                      String locationId, String skuId, long delta,
                                      String warehouseCode) {
        Map<String, Object> env = baseEnvelope(eventId, "inventory.adjusted",
                occurredAt, "inventory", locationId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("locationId", locationId);
        payload.put("skuId", skuId);
        payload.put("delta", delta);
        if (warehouseCode != null) {
            payload.put("warehouseCode", warehouseCode);
        }
        env.put("payload", payload);
        return toJson(env);
    }

    /**
     * Build the wms-platform global event envelope JSON for an
     * {@code inventory.received.v1} event with a single line.
     */
    protected String receivedEnvelope(UUID eventId, Instant occurredAt,
                                      String warehouseId, String skuId, long qtyReceived) {
        return receivedEnvelope(eventId, occurredAt, warehouseId, skuId, qtyReceived, null);
    }

    /**
     * As {@link #receivedEnvelope(UUID, Instant, String, String, long)}, with the additive
     * {@code warehouseCode} (ADR-MONO-050 D9 / TASK-SCM-BE-037). Pass {@code null} to omit
     * the field entirely, mirroring an older wms producer.
     */
    protected String receivedEnvelope(UUID eventId, Instant occurredAt,
                                      String warehouseId, String skuId, long qtyReceived,
                                      String warehouseCode) {
        Map<String, Object> env = baseEnvelope(eventId, "inventory.received",
                occurredAt, "inventory", warehouseId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("warehouseId", warehouseId);
        if (warehouseCode != null) {
            payload.put("warehouseCode", warehouseCode);
        }
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("skuId", skuId);
        line.put("qtyReceived", qtyReceived);
        payload.put("lines", List.of(line));
        env.put("payload", payload);
        return toJson(env);
    }

    private Map<String, Object> baseEnvelope(UUID eventId, String eventType,
                                              Instant occurredAt, String aggregateType,
                                              String aggregateId) {
        Map<String, Object> env = new LinkedHashMap<>();
        env.put("eventId", eventId.toString());
        env.put("eventType", eventType);
        env.put("eventVersion", 1);
        env.put("occurredAt", occurredAt.toString());
        env.put("producer", "inventory-service");
        env.put("aggregateType", aggregateType);
        env.put("aggregateId", aggregateId);
        env.put("traceId", null);
        env.put("actorId", "test-actor");
        return env;
    }

    private String toJson(Map<String, Object> env) {
        try {
            return objectMapper.writeValueAsString(env);
        } catch (Exception e) {
            throw new IllegalStateException("envelope serialise failed", e);
        }
    }

    /**
     * Send a single record to a Kafka topic via a one-shot {@link KafkaProducer}.
     * Returns when the broker has acknowledged the publish.
     */
    protected void publish(String topic, String key, String value) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            producer.send(new ProducerRecord<>(topic, key, value)).get(15,
                    java.util.concurrent.TimeUnit.SECONDS);
            producer.flush();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to publish to " + topic, e);
        }
    }

    /**
     * Persist an {@code inventory_nodes} row directly (bypassing the
     * application service) for test setup that needs a known node id.
     */
    protected InventoryNodeJpaEntity persistNode(String tenantId, String externalId) {
        InventoryNodeJpaEntity e = new InventoryNodeJpaEntity();
        e.setId(UUID.randomUUID().toString());
        e.setTenantId(tenantId);
        e.setNodeExternalId(externalId);
        e.setNodeType(InventoryNodeJpaEntity.NodeTypeJpa.WMS_WAREHOUSE);
        e.setName("");
        e.setStatus(InventoryNodeJpaEntity.NodeStatusJpa.ACTIVE);
        e.setContactInfo(null);
        Instant now = Instant.now();
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        return nodeJpa.save(e);
    }

    /**
     * Persist a {@code node_staleness} row whose {@code last_event_at} is
     * older than the threshold (10 minutes), so the next staleness detection
     * batch flips its status to STALE.
     */
    protected NodeStalenessJpaEntity persistStaleNodeStaleness(String tenantId, String nodeId,
                                                                 Instant lastEventAt) {
        NodeStalenessJpaEntity e = new NodeStalenessJpaEntity();
        e.setNodeId(nodeId);
        e.setTenantId(tenantId);
        e.setLastEventAt(lastEventAt);
        e.setLastEventId(UUID.randomUUID().toString());
        e.setStalenessStatus(NodeStalenessJpaEntity.StalenessStatusJpa.FRESH);
        e.setLastCheckedAt(lastEventAt);
        return stalenessJpa.save(e);
    }
}
