package com.example.scmplatform.demandplanning.integration;

import com.example.scmplatform.demandplanning.adapter.outbound.persistence.jpa.ProcessedEventJpaRepository;
import com.example.scmplatform.demandplanning.adapter.outbound.persistence.jpa.ReorderPolicyJpaRepository;
import com.example.scmplatform.demandplanning.adapter.outbound.persistence.jpa.ReorderSuggestionJpaRepository;
import com.example.scmplatform.demandplanning.adapter.outbound.persistence.jpa.SkuSupplierMappingJpaRepository;
import com.example.testsupport.integration.DockerAvailableCondition;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
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
 * Base class for demand-planning-service integration tests.
 * Mirrors AbstractInventoryVisibilityIntegrationTest pattern.
 * Shared Postgres + Kafka containers (started once per JVM via static block).
 */
@Tag("integration")
@ExtendWith(DockerAvailableCondition.class)
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class AbstractDemandPlanningIntegrationTest {

    protected static final String TENANT_SCM = "scm";
    protected static final String TOPIC_ALERT = "wms.inventory.alert.v1";
    protected static final String TOPIC_ALERT_DLT = "wms.inventory.alert.v1.DLT";
    // ADR-MONO-050 D9: the additive warehouse CODE the alert now carries (→ PO destination).
    protected static final String ALERT_WAREHOUSE_CODE = "WH-IT-01";

    @SuppressWarnings("resource")
    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("scm_demand_planning")
                    .withUsername("scm")
                    .withPassword("scm")
                    .withStartupTimeout(Duration.ofMinutes(3));

    protected static final ConfluentKafkaContainer KAFKA =
            new ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"))
                    .withStartupTimeout(Duration.ofMinutes(3));

    static {
        POSTGRES.start();
        KAFKA.start();
        preCreateTopics();
    }

    private static void preCreateTopics() {
        Properties props = new Properties();
        props.put("bootstrap.servers", KAFKA.getBootstrapServers());
        try (AdminClient admin = AdminClient.create(props)) {
            admin.createTopics(List.of(
                    new NewTopic(TOPIC_ALERT, 1, (short) 1),
                    new NewTopic(TOPIC_ALERT_DLT, 1, (short) 1)
            )).all().get(30, java.util.concurrent.TimeUnit.SECONDS);
        } catch (ExecutionException e) {
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

        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration/demand-planning");

        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);

        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> "http://localhost:9999/oauth2/jwks");
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                () -> "http://test-issuer");
        registry.add("scmplatform.oauth2.allowed-issuers", () -> "http://test-issuer");

        // Disable nightly sweep so IT controls timing explicitly
        registry.add("demand-planning.sweep.cron", () -> "0 0 2 31 2 *"); // never fires
    }

    @Autowired
    protected ReorderSuggestionJpaRepository suggestionJpa;

    @Autowired
    protected ReorderPolicyJpaRepository policyJpa;

    @Autowired
    protected SkuSupplierMappingJpaRepository mappingJpa;

    @Autowired
    protected ProcessedEventJpaRepository processedEventJpa;

    @Autowired
    protected ObjectMapper objectMapper;

    /**
     * Reset all tables before each test. @DirtiesContext(AFTER_CLASS) reuses the
     * containers across the class, so without this the per-test @BeforeEach seeds
     * accumulate and re-seeding a fixed key (e.g. reorder_policy) collides. Runs in
     * the superclass so it executes before each subclass's own @BeforeEach seed.
     */
    @BeforeEach
    void cleanDatabase() {
        processedEventJpa.deleteAll();
        suggestionJpa.deleteAll();
        policyJpa.deleteAll();
        mappingJpa.deleteAll();
    }

    /**
     * Build a wms inventory.low-stock-detected envelope (camelCase, wms convention).
     */
    protected String alertEnvelope(UUID eventId, String skuCode, String locationId,
                                    int availableQty, int threshold) {
        Map<String, Object> env = new LinkedHashMap<>();
        env.put("eventId", eventId.toString());
        env.put("eventType", "inventory.low-stock-detected");
        env.put("eventVersion", 1);
        env.put("occurredAt", Instant.now().toString());
        env.put("producer", "inventory-service");
        env.put("aggregateType", "inventory");
        env.put("aggregateId", locationId);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("skuId", UUID.randomUUID().toString());
        payload.put("skuCode", skuCode);
        payload.put("locationId", locationId);
        payload.put("locationCode", "WH-A");
        // ADR-MONO-050 D9: additive warehouse CODE the consumer threads to the PO destination.
        payload.put("warehouseCode", ALERT_WAREHOUSE_CODE);
        payload.put("availableQty", availableQty);
        payload.put("threshold", threshold);
        payload.put("triggeringEventType", "inventory.adjusted");
        env.put("payload", payload);

        try {
            return objectMapper.writeValueAsString(env);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize alert envelope", e);
        }
    }

    protected void publish(String topic, String key, String value) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            producer.send(new ProducerRecord<>(topic, key, value))
                    .get(15, java.util.concurrent.TimeUnit.SECONDS);
            producer.flush();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to publish to " + topic, e);
        }
    }
}
