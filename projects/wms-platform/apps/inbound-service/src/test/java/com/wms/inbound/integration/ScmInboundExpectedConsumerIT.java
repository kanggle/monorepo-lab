package com.wms.inbound.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.test.context.TestPropertySource;

/**
 * ADR-MONO-050 Phase 2 (TASK-SCM-INT-004) — <b>wms-consumer</b> leg of the
 * inbound-expected closed loop, PR-gated + deterministic (real Kafka + real
 * Postgres via Testcontainers).
 *
 * <p>This is the authoritative deterministic guard for the <em>wms half</em> of
 * the loop: the byte-exact {@code scm.procurement.inbound-expected.v1} envelope
 * that scm procurement emits (its shape is guarded on the scm side by
 * {@code InboundExpectedLoopE2ETest}) is injected onto a real broker, and the
 * REAL {@code inbound-service} consumer + parser + dedupe + create/cancel
 * use-cases turn it into an {@code Asn} inbound expectation against real Postgres.
 * The existing {@code ScmInboundExpectedConsumerTest} /
 * {@code CreateScmInboundExpectationServiceTest} cover the same rules with mocks;
 * this IT is the end-to-end wiring proof (envelope → Kafka → consumer → DB) that
 * a mock cannot give. It mirrors the ecommerce→wms {@code FulfillmentRequestedConsumerIT}
 * cross-project consumer-IT precedent.
 *
 * <p>Shared-code seeding is the crux (ADR-050 D9): a warehouse CODE, a supplier
 * CODE (ACTIVE {@code SUPPLIER} partner) and a SKU code are seeded into the
 * inbound read-model — exactly what the {@code wms.master.*} projection would
 * populate — so the codes carried on the scm event resolve on the wms side.
 *
 * <p>Cases: happy (Asn CREATED, source SCM_PROCUREMENT, resolved warehouse, PO
 * trace); multi-warehouse routing; single-warehouse; eventId idempotency;
 * {@code (poNumber,line)} business dedup; unknown-warehouse fail-closed (no Asn);
 * 3PL {@code destinationNodeType} reject (no Asn); cancel → CANCELLED.
 */
@TestPropertySource(properties = {
        // Deterministic publish→@KafkaListener consumption: read from the
        // beginning and refresh metadata fast so a topic created in @BeforeEach is
        // discovered well within the await (default metadata.max.age is 5 min).
        // Mirrors OutboundServiceIntegrationBase; complements the explicit topic
        // pre-creation + waitForAssignment below.
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.kafka.consumer.properties.metadata.max.age.ms=2000",
        // Keep the OTHER inbound listeners out of the group churn — only the scm
        // containers this IT explicitly starts should join (mirrors
        // OutboundServiceIntegrationBase / TASK-MONO-376). Without this, every
        // auto-started sibling listener joining/leaving revokes the container under
        // test's assignment and waitForAssignment never converges on a loaded runner.
        "spring.kafka.listener.auto-startup=false"
})
class ScmInboundExpectedConsumerIT extends InboundServiceIntegrationBase {

    private static final String TOPIC = "scm.procurement.inbound-expected.v1";
    private static final String TOPIC_CANCELLED = "scm.procurement.inbound-expected.cancelled.v1";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private KafkaListenerEndpointRegistry listenerRegistry;

    private KafkaProducer<String, String> producer;

    @BeforeEach
    void setUp() {
        createTopics();
        waitForScmListenerAssignment();
        // asn / asn_line are ordinary tables; inbound_event_dedupe + inbound_outbox
        // are append-only (W2) → TRUNCATE bypasses the BEFORE DELETE triggers.
        jdbc.execute("TRUNCATE TABLE asn CASCADE");
        jdbc.execute("TRUNCATE TABLE inbound_event_dedupe");
        jdbc.execute("TRUNCATE TABLE inbound_outbox");
        jdbc.update("DELETE FROM warehouse_snapshot");
        jdbc.update("DELETE FROM partner_snapshot");
        jdbc.update("DELETE FROM sku_snapshot");
    }

    @AfterEach
    void tearDown() {
        if (producer != null) {
            producer.close();
        }
    }

    // ------------------------------------------------------------------ happy

    @Test
    @DisplayName("inbound-expected event -> Asn(CREATED, SCM_PROCUREMENT) at the resolved warehouse")
    void inboundExpectedCreatesAsnAtResolvedWarehouse() throws Exception {
        UUID whId = seedWarehouse("WH-SEOUL-01");
        UUID supplierId = seedSupplier("SUP-0043");
        UUID skuId = seedSku("SKU-APPLE-001");
        String poNumber = uniquePo();
        UUID poId = UUID.randomUUID();

        publish(TOPIC, poId.toString(), inboundExpected(UUID.randomUUID(), poId, poNumber,
                "SUP-0043", "WH-SEOUL-01", "WMS_WAREHOUSE", "SKU-APPLE-001", "100"));

        Map<String, Object> asn = awaitAsn(poNumber);
        assertThat(asn.get("source")).isEqualTo("SCM_PROCUREMENT");
        assertThat(asn.get("status")).isEqualTo("CREATED");
        assertThat(asn.get("warehouse_id")).isEqualTo(whId);
        assertThat(asn.get("supplier_partner_id")).isEqualTo(supplierId);
        assertThat(asn.get("po_number")).isEqualTo(poNumber);
        assertThat(asn.get("po_id")).isEqualTo(poId);

        UUID asnId = (UUID) asn.get("id");
        Map<String, Object> line = jdbc.queryForMap(
                "SELECT sku_id, expected_qty FROM asn_line WHERE asn_id = ?", asnId);
        assertThat(line.get("sku_id")).isEqualTo(skuId);
        assertThat(((Number) line.get("expected_qty")).intValue()).isEqualTo(100);
    }

    // ------------------------------------------------ D3 warehouse addressing

    @Test
    @DisplayName("two POs to two warehouse codes -> two Asns each at its addressed warehouse")
    void multiWarehouseRoutesEachToItsWarehouse() throws Exception {
        UUID whA = seedWarehouse("WH-A");
        UUID whB = seedWarehouse("WH-B");
        seedSupplier("SUP-0043");
        seedSku("SKU-A");
        String poA = uniquePo();
        String poB = uniquePo();

        publish(TOPIC, poA, inboundExpected(UUID.randomUUID(), UUID.randomUUID(), poA,
                "SUP-0043", "WH-A", "WMS_WAREHOUSE", "SKU-A", "10"));
        publish(TOPIC, poB, inboundExpected(UUID.randomUUID(), UUID.randomUUID(), poB,
                "SUP-0043", "WH-B", "WMS_WAREHOUSE", "SKU-A", "20"));

        assertThat(awaitAsn(poA).get("warehouse_id")).isEqualTo(whA);
        assertThat(awaitAsn(poB).get("warehouse_id")).isEqualTo(whB);
    }

    @Test
    @DisplayName("two POs to the same warehouse code -> two Asns at that one warehouse")
    void singleWarehouseRoutesBothToSameWarehouse() throws Exception {
        UUID wh = seedWarehouse("WH-ONLY");
        seedSupplier("SUP-0043");
        seedSku("SKU-A");
        String po1 = uniquePo();
        String po2 = uniquePo();

        publish(TOPIC, po1, inboundExpected(UUID.randomUUID(), UUID.randomUUID(), po1,
                "SUP-0043", "WH-ONLY", "WMS_WAREHOUSE", "SKU-A", "10"));
        publish(TOPIC, po2, inboundExpected(UUID.randomUUID(), UUID.randomUUID(), po2,
                "SUP-0043", "WH-ONLY", "WMS_WAREHOUSE", "SKU-A", "20"));

        assertThat(awaitAsn(po1).get("warehouse_id")).isEqualTo(wh);
        assertThat(awaitAsn(po2).get("warehouse_id")).isEqualTo(wh);
    }

    // ------------------------------------------------------- D6 idempotency

    @Test
    @DisplayName("same eventId redelivered -> exactly one Asn (eventId dedup, D6.1)")
    void duplicateEventIdCreatesExactlyOneAsn() throws Exception {
        seedWarehouse("WH-A");
        seedSupplier("SUP-0043");
        seedSku("SKU-A");
        String po = uniquePo();
        UUID eventId = UUID.randomUUID();
        UUID poId = UUID.randomUUID();

        String event = inboundExpected(eventId, poId, po, "SUP-0043", "WH-A", "WMS_WAREHOUSE", "SKU-A", "10");
        publish(TOPIC, po, event);
        awaitAsn(po);
        publish(TOPIC, po, event); // exact redelivery — same envelope eventId

        // Give the second delivery time to be (not) applied, then assert count is still 1.
        assertEventuallyStableAsnCount(po, 1);
    }

    @Test
    @DisplayName("distinct eventIds, same poNumber -> exactly one open Asn (business dedup, D6.2)")
    void businessDuplicatePoNumberCreatesExactlyOneAsn() throws Exception {
        seedWarehouse("WH-A");
        seedSupplier("SUP-0043");
        seedSku("SKU-A");
        String po = uniquePo();

        publish(TOPIC, po, inboundExpected(UUID.randomUUID(), UUID.randomUUID(), po,
                "SUP-0043", "WH-A", "WMS_WAREHOUSE", "SKU-A", "10"));
        awaitAsn(po);
        // Different eventId, same poNumber — must not create a second open expectation.
        publish(TOPIC, po, inboundExpected(UUID.randomUUID(), UUID.randomUUID(), po,
                "SUP-0043", "WH-A", "WMS_WAREHOUSE", "SKU-A", "10"));

        assertEventuallyStableAsnCount(po, 1);
    }

    // ------------------------------------------------ D3/D4 fail-closed rejects

    @Test
    @DisplayName("unknown warehouse code -> fail-closed, no Asn created (routes to DLT)")
    void unknownWarehouseCreatesNoAsn() throws Exception {
        seedWarehouse("WH-KNOWN");
        seedSupplier("SUP-0043");
        seedSku("SKU-A");
        String bad = uniquePo();
        String good = uniquePo();

        publish(TOPIC, bad, inboundExpected(UUID.randomUUID(), UUID.randomUUID(), bad,
                "SUP-0043", "WH-GHOST", "WMS_WAREHOUSE", "SKU-A", "10"));
        // Barrier: a subsequent good event proves the consumer processed past the bad one.
        publish(TOPIC, good, inboundExpected(UUID.randomUUID(), UUID.randomUUID(), good,
                "SUP-0043", "WH-KNOWN", "WMS_WAREHOUSE", "SKU-A", "10"));

        awaitAsn(good);
        assertThat(asnCount(bad)).as("unknown warehouse -> no Asn (fail-closed)").isZero();
    }

    @Test
    @DisplayName("3PL destinationNodeType -> defensive reject, no Asn created")
    void thirdPartyNodeTypeCreatesNoAsn() throws Exception {
        seedWarehouse("WH-A");
        seedSupplier("SUP-0043");
        seedSku("SKU-A");
        String bad = uniquePo();
        String good = uniquePo();

        publish(TOPIC, bad, inboundExpected(UUID.randomUUID(), UUID.randomUUID(), bad,
                "SUP-0043", "WH-A", "THIRD_PARTY_LOGISTICS", "SKU-A", "10"));
        publish(TOPIC, good, inboundExpected(UUID.randomUUID(), UUID.randomUUID(), good,
                "SUP-0043", "WH-A", "WMS_WAREHOUSE", "SKU-A", "10"));

        awaitAsn(good);
        assertThat(asnCount(bad)).as("3PL nodeType -> defensive reject, no Asn").isZero();
    }

    // ------------------------------------------------------------- D6.3 cancel

    @Test
    @DisplayName("inbound-expected.cancelled -> the open Asn is marked CANCELLED")
    void cancelledEventMarksAsnCancelled() throws Exception {
        seedWarehouse("WH-A");
        seedSupplier("SUP-0043");
        seedSku("SKU-A");
        String po = uniquePo();

        publish(TOPIC, po, inboundExpected(UUID.randomUUID(), UUID.randomUUID(), po,
                "SUP-0043", "WH-A", "WMS_WAREHOUSE", "SKU-A", "10"));
        Map<String, Object> created = awaitAsn(po);
        assertThat(created.get("status")).isEqualTo("CREATED");

        publish(TOPIC_CANCELLED, po, cancelled(UUID.randomUUID(), UUID.randomUUID(), po, "SKU-A"));

        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    String status = jdbc.queryForObject(
                            "SELECT status FROM asn WHERE po_number = ?", String.class, po);
                    assertThat(status).isEqualTo("CANCELLED");
                });
    }

    // ==================================================================
    // helpers
    // ==================================================================

    private Map<String, Object> awaitAsn(String poNumber) {
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> assertThat(asnCount(poNumber))
                        .as("an Asn exists for poNumber %s", poNumber).isEqualTo(1));
        return jdbc.queryForMap("SELECT * FROM asn WHERE po_number = ?", poNumber);
    }

    private int asnCount(String poNumber) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM asn WHERE po_number = ?", Integer.class, poNumber);
        return n == null ? 0 : n;
    }

    /** Waits briefly, then asserts the Asn count for {@code poNumber} settled at {@code expected}. */
    private void assertEventuallyStableAsnCount(String poNumber, int expected) {
        // The (non-)creation is eventual; poll a stable window so a late second
        // create would be caught rather than raced past.
        await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> assertThat(asnCount(poNumber)).isEqualTo(expected));
    }

    private UUID seedWarehouse(String code) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO warehouse_snapshot (id, warehouse_code, status, cached_at, master_version)
                VALUES (?, ?, 'ACTIVE', now(), 1)
                """, id, code);
        return id;
    }

    private UUID seedSupplier(String code) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO partner_snapshot (id, partner_code, partner_type, status, cached_at, master_version)
                VALUES (?, ?, 'SUPPLIER', 'ACTIVE', now(), 1)
                """, id, code);
        return id;
    }

    private UUID seedSku(String code) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO sku_snapshot (id, sku_code, tracking_type, status, cached_at, master_version)
                VALUES (?, ?, 'NONE', 'ACTIVE', now(), 1)
                """, id, code);
        return id;
    }

    private static String uniquePo() {
        return "SCM-PO-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
    }

    /**
     * Byte-exact scm envelope (7-field {@code OutboxProcurementEventPublisher}
     * shape) — {@code expectedQty} is a plain DECIMAL STRING (ADR-050 D9).
     */
    private static String inboundExpected(UUID eventId, UUID poId, String poNumber,
                                          String supplierCode, String warehouseCode,
                                          String nodeType, String skuCode, String expectedQty) {
        return """
                {
                  "eventId": "%s",
                  "eventType": "scm.procurement.inbound-expected",
                  "source": "scm-platform-procurement-service",
                  "occurredAt": "2026-07-19T04:12:00.000Z",
                  "schemaVersion": 1,
                  "partitionKey": "%s",
                  "payload": {
                    "poId": "%s",
                    "poNumber": "%s",
                    "supplierId": "%s",
                    "destinationWarehouseId": "%s",
                    "destinationNodeType": "%s",
                    "expectedArrivalDate": "2026-07-24",
                    "currency": "KRW",
                    "lines": [
                      { "skuCode": "%s", "expectedQty": "%s", "uom": "EA" }
                    ]
                  }
                }
                """.formatted(eventId, poId, poId, poNumber, supplierCode, warehouseCode,
                nodeType, skuCode, expectedQty);
    }

    private static String cancelled(UUID eventId, UUID poId, String poNumber, String skuCode) {
        return """
                {
                  "eventId": "%s",
                  "eventType": "scm.procurement.inbound-expected.cancelled",
                  "source": "scm-platform-procurement-service",
                  "occurredAt": "2026-07-19T05:00:00.000Z",
                  "schemaVersion": 1,
                  "partitionKey": "%s",
                  "payload": {
                    "poId": "%s",
                    "poNumber": "%s",
                    "lines": [ { "skuCode": "%s" } ]
                  }
                }
                """.formatted(eventId, poId, poId, poNumber, skuCode);
    }

    private void createTopics() {
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        try (AdminClient admin = AdminClient.create(props)) {
            admin.createTopics(List.of(
                    new NewTopic(TOPIC, 1, (short) 1),
                    new NewTopic(TOPIC_CANCELLED, 1, (short) 1),
                    new NewTopic(TOPIC + ".DLT", 1, (short) 1),
                    new NewTopic(TOPIC_CANCELLED + ".DLT", 1, (short) 1)
            )).all().get(20, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            if (!(e.getCause() instanceof TopicExistsException)) {
                throw new IllegalStateException("Failed to create scm inbound-expected topics", e);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted creating topics", e);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create scm inbound-expected topics", e);
        }
    }

    /**
     * Blocks until both scm-inbound-expected listener containers hold their partition.
     *
     * <p>Both listeners share group {@code wms-inbound-scm-expected-v1}, so all needed
     * containers must be <b>started first</b> and only then awaited: starting-then-awaiting
     * one at a time makes the second member's join revoke the first's assignment, which never
     * converges on a loaded CI runner (the failure mode documented in
     * {@code OutboundServiceIntegrationBase}). Combined with {@code auto-startup=false}, the
     * group forms once with exactly these two members and a single rebalance settles both.
     */
    private void waitForScmListenerAssignment() {
        List<MessageListenerContainer> scmContainers = new ArrayList<>();
        for (MessageListenerContainer c : listenerRegistry.getListenerContainers()) {
            String[] topics = c.getContainerProperties().getTopics();
            if (topics == null) {
                continue;
            }
            List<String> t = Arrays.asList(topics);
            if (t.contains(TOPIC) || t.contains(TOPIC_CANCELLED)) {
                scmContainers.add(c);
            }
        }
        for (MessageListenerContainer c : scmContainers) {
            if (!c.isRunning()) {
                c.start();
            }
        }
        for (MessageListenerContainer c : scmContainers) {
            ContainerTestUtils.waitForAssignment(c, 1);
        }
    }

    private void publish(String topic, String key, String json) throws Exception {
        if (producer == null) {
            Properties props = new Properties();
            props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
            props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            producer = new KafkaProducer<>(props);
        }
        producer.send(new ProducerRecord<>(topic, key, json)).get(10, TimeUnit.SECONDS);
        producer.flush();
    }
}
