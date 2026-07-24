package com.example.scmplatform.logistics.integration;

import com.example.scmplatform.logistics.application.port.outbound.DispatchPersistencePort;
import com.example.scmplatform.logistics.domain.model.Dispatch;
import com.example.scmplatform.logistics.domain.model.ShipmentId;
import com.example.testsupport.integration.DockerAvailableCondition;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/**
 * Base for logistics-service integration tests. Shared Postgres (Flyway V1 → {@code validate}),
 * a WireMock EasyPost + 굿스플로 stand-in, and a Kafka container (BE-044: the seam consumer is now
 * wired, so the {@code @KafkaListener} needs a broker in every IT context), started once per JVM.
 *
 * <p>The {@code shipping.confirmed} seam topic + its DLT are pre-created; the
 * {@link #publish}/{@link #shippingConfirmedEnvelope}/{@link #drain} helpers drive the consumer IT.
 * Non-consumer ITs (routing, retry, WireMock matrix) still seed dispatch rows directly and simply
 * leave the (idle) consumer connected.
 *
 * <p>Windows local = not authority: these tests SKIP without Docker; CI Linux is the gate.
 */
@Tag("integration")
@ExtendWith(DockerAvailableCondition.class)
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class AbstractLogisticsIntegrationTest {

    protected static final String TENANT_SCM = "scm";

    /** The seam topic + its DLT (subscriptions contract § Subscribed Topic / § Retry + DLT). */
    protected static final String TOPIC = "wms.outbound.shipping.confirmed.v1";
    protected static final String TOPIC_DLT = TOPIC + ".DLT";

    @SuppressWarnings("resource")
    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("scm_logistics")
                    .withUsername("scm")
                    .withPassword("scm")
                    .withStartupTimeout(Duration.ofMinutes(3));

    protected static final ConfluentKafkaContainer KAFKA =
            new ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"))
                    .withStartupTimeout(Duration.ofMinutes(3));

    protected static final WireMockServer EASYPOST =
            new WireMockServer(wireMockConfig().dynamicPort());

    /**
     * Independent 굿스플로 stub — a SEPARATE WireMock from EASYPOST (I9: the two vendors share no
     * pool/circuit/stub). Both {@code !standalone} adapter beans load in every IT, so both vendor
     * base-urls point at a stub; a test drives whichever the {@code CarrierRouter} selects.
     */
    protected static final WireMockServer GOODSFLOW =
            new WireMockServer(wireMockConfig().dynamicPort());

    static {
        POSTGRES.start();
        EASYPOST.start();
        GOODSFLOW.start();
        KAFKA.start();
        preCreateTopics();
    }

    private static void preCreateTopics() {
        Properties props = new Properties();
        props.put("bootstrap.servers", KAFKA.getBootstrapServers());
        try (AdminClient admin = AdminClient.create(props)) {
            admin.createTopics(List.of(
                    new NewTopic(TOPIC, 1, (short) 1),
                    new NewTopic(TOPIC_DLT, 1, (short) 1)
            )).all().get(30, TimeUnit.SECONDS);
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
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration/logistics");

        // EasyPost → WireMock. Fast timeouts + fast retry backoff so the resilience matrix runs
        // quickly under CI (still exercises retry/circuit/bulkhead).
        registry.add("logistics.easypost.base-url", EASYPOST::baseUrl);
        registry.add("logistics.easypost.connect-timeout-seconds", () -> "1");
        registry.add("logistics.easypost.read-timeout-seconds", () -> "2");
        registry.add("resilience4j.retry.instances.easyPostDispatch.wait-duration", () -> "100ms");
        registry.add("resilience4j.retry.instances.easyPostDispatch.exponential-max-wait-duration", () -> "300ms");

        // 굿스플로 → its OWN WireMock, fast timeouts + fast retry backoff (independent instance, I9).
        registry.add("logistics.goodsflow.base-url", GOODSFLOW::baseUrl);
        registry.add("logistics.goodsflow.connect-timeout-seconds", () -> "1");
        registry.add("logistics.goodsflow.read-timeout-seconds", () -> "2");
        registry.add("resilience4j.retry.instances.goodsflowDispatch.wait-duration", () -> "100ms");
        registry.add("resilience4j.retry.instances.goodsflowDispatch.exponential-max-wait-duration", () -> "300ms");

        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> "http://localhost:9999/oauth2/jwks");
        registry.add("scmplatform.oauth2.allowed-issuers", () -> "http://test-issuer");
    }

    @Autowired
    protected DispatchPersistencePort persistencePort;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected ObjectMapper objectMapper;

    /** Wipe all logistics tables between tests (containers are reused across the class). */
    protected void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE dispatch, dispatch_request_dedupe, processed_events");
    }

    /** Seed a PENDING dispatch row directly (the trigger is the BE-044 event; no create endpoint). */
    protected Dispatch seedPending(UUID shipmentId, String shipmentNo) {
        Dispatch dispatch = Dispatch.create(UUID.randomUUID(), ShipmentId.of(shipmentId),
                shipmentNo, UUID.randomUUID(), "ORD-" + shipmentNo, TENANT_SCM, Instant.now());
        return persistencePort.save(dispatch);
    }

    /** Seed a PENDING dispatch row with a specific requested carrier code (routing input). */
    protected Dispatch seedPending(UUID shipmentId, String shipmentNo, String requestedCarrierCode) {
        Dispatch dispatch = Dispatch.create(UUID.randomUUID(), ShipmentId.of(shipmentId),
                shipmentNo, UUID.randomUUID(), "ORD-" + shipmentNo, TENANT_SCM,
                requestedCarrierCode, Instant.now());
        return persistencePort.save(dispatch);
    }

    /** Seed a DISPATCH_FAILED dispatch row (a prior vendor failure awaiting operator :retry). */
    protected Dispatch seedFailed(UUID shipmentId, String shipmentNo) {
        return seedFailed(shipmentId, shipmentNo, null);
    }

    /**
     * Seed a DISPATCH_FAILED dispatch row with a specific requested carrier code, so {@code :retry}
     * re-routes deterministically from the stored signal (BE-043 routing IT).
     */
    protected Dispatch seedFailed(UUID shipmentId, String shipmentNo, String requestedCarrierCode) {
        Dispatch dispatch = Dispatch.create(UUID.randomUUID(), ShipmentId.of(shipmentId),
                shipmentNo, UUID.randomUUID(), "ORD-" + shipmentNo, TENANT_SCM,
                requestedCarrierCode, Instant.now());
        dispatch.recordFailure("seeded prior failure", Instant.now());
        return persistencePort.save(dispatch);
    }

    // ── Kafka seam helpers (BE-044 consumer IT) ─────────────────────────────────────────────────

    /** Publish a raw value to a topic, keyed by shipmentId (the wms partition key semantics). */
    protected void publish(String topic, String key, String value) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            producer.send(new ProducerRecord<>(topic, key, value)).get(15, TimeUnit.SECONDS);
            producer.flush();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to publish to " + topic, e);
        }
    }

    /**
     * Build a wms {@code outbound.shipping.confirmed} envelope (camelCase, wms convention). A
     * {@code null} {@code eventId} or {@code null} {@code shipmentId} models a malformed envelope
     * (the key is omitted so it deserializes to null → non-retryable DLT). A {@code null}
     * {@code carrierCode} models the nullable-at-source case (→ default vendor + degrade).
     */
    protected String shippingConfirmedEnvelope(UUID eventId, String tenantId, UUID shipmentId,
                                               String shipmentNo, String carrierCode) {
        Map<String, Object> env = new LinkedHashMap<>();
        if (eventId != null) {
            env.put("eventId", eventId.toString());
        }
        env.put("eventType", "outbound.shipping.confirmed");
        env.put("eventVersion", 1);
        env.put("occurredAt", Instant.now().toString());
        env.put("producer", "outbound-service");
        env.put("aggregateType", "shipment");
        env.put("aggregateId", shipmentId == null ? null : shipmentId.toString());
        if (tenantId != null) {
            env.put("tenantId", tenantId);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        if (shipmentId != null) {
            payload.put("shipmentId", shipmentId.toString());
        }
        payload.put("shipmentNo", shipmentNo);
        payload.put("orderId", UUID.randomUUID().toString());
        payload.put("orderNo", "ORD-" + shipmentNo);
        payload.put("warehouseId", UUID.randomUUID().toString());
        if (carrierCode != null) {
            payload.put("carrierCode", carrierCode);
        }
        payload.put("shippedAt", Instant.now().toString());
        List<Map<String, Object>> lines = new ArrayList<>();
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("orderLineId", UUID.randomUUID().toString());
        line.put("skuId", UUID.randomUUID().toString());
        line.put("lotId", UUID.randomUUID().toString());
        line.put("locationId", UUID.randomUUID().toString());
        line.put("qtyConfirmed", 1000);
        lines.add(line);
        payload.put("lines", lines);
        env.put("payload", payload);

        try {
            return objectMapper.writeValueAsString(env);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize shipping.confirmed envelope", e);
        }
    }

    /**
     * Drain a topic from the beginning (fresh random group), collecting records within the timeout.
     * The topics are NOT truncated between tests (only the DB is), so callers filter the returned
     * records by a per-test marker (e.g. the unique {@code shipmentNo}).
     */
    protected List<ConsumerRecord<String, String>> drain(String topic, Duration timeout) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "it-drain-" + UUID.randomUUID());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        List<ConsumerRecord<String, String>> out = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(topic));
            long deadline = System.currentTimeMillis() + timeout.toMillis();
            while (System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, String> polled = consumer.poll(Duration.ofMillis(300));
                polled.forEach(out::add);
            }
        }
        return out;
    }

    /** Count DLT records whose raw value contains the given marker (e.g. a unique shipmentNo). */
    protected long dltRecordsContaining(String marker, Duration timeout) {
        return drain(TOPIC_DLT, timeout).stream()
                .filter(r -> r.value() != null && r.value().contains(marker))
                .count();
    }
}
