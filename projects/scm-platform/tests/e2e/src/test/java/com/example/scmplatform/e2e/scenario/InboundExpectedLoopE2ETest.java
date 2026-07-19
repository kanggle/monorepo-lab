package com.example.scmplatform.e2e.scenario;

import static com.example.scmplatform.e2e.testsupport.E2ETestFixtures.authedJson;
import static com.example.scmplatform.e2e.testsupport.E2ETestFixtures.pathDemandPlanningApprove;
import static com.example.scmplatform.e2e.testsupport.E2ETestFixtures.pathDemandPlanningPolicy;
import static com.example.scmplatform.e2e.testsupport.E2ETestFixtures.pathDemandPlanningSkuSupplierMap;
import static com.example.scmplatform.e2e.testsupport.E2ETestFixtures.pathDemandPlanningSuggestions;
import static com.example.scmplatform.e2e.testsupport.E2ETestFixtures.pathProcurementPo;
import static com.example.scmplatform.e2e.testsupport.E2ETestFixtures.pathProcurementPoConfirm;
import static com.example.scmplatform.e2e.testsupport.E2ETestFixtures.pathProcurementPoSubmit;
import static com.example.scmplatform.e2e.testsupport.E2ETestFixtures.pathSupplierAckWebhook;
import static com.example.scmplatform.e2e.testsupport.E2ETestFixtures.authedGet;
import static com.example.scmplatform.e2e.testsupport.E2ETestFixtures.randomAccountId;
import static com.example.scmplatform.e2e.testsupport.E2ETestFixtures.randomLocationId;
import static com.example.scmplatform.e2e.testsupport.E2ETestFixtures.sendString;
import static com.example.scmplatform.e2e.testsupport.E2ETestFixtures.uniqueIdempotencyKey;
import static com.example.scmplatform.e2e.testsupport.E2ETestFixtures.uniqueSku;
import static com.example.scmplatform.e2e.testsupport.E2ETestFixtures.uniqueSupplierAckRef;
import static com.example.scmplatform.e2e.testsupport.E2ETestFixtures.webhookSignedPost;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.scmplatform.e2e.testsupport.KafkaTestConsumer;
import com.example.scmplatform.e2e.testsupport.KafkaTestProducer;
import com.example.scmplatform.e2e.testsupport.ProcurementDbFixtures;
import com.example.scmplatform.e2e.testsupport.ScmPlatformE2ETestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * ADR-MONO-050 Phase 2 (TASK-SCM-INT-004) — <b>scm-producer</b> leg of the
 * inbound-expected closed loop, PR-gated + deterministic.
 *
 * <p><b>Which pattern + why.</b> The scm {@code tests/e2e} harness boots only the
 * scm services (gateway + procurement + demand-planning + inventory-visibility);
 * wms is a different project and is <em>not</em> booted here. This mirrors the
 * ADR-MONO-027 replenishment precedent exactly, only with the roles reversed:
 * there wms was faked by a host {@link KafkaTestProducer} and the real scm
 * <em>consumer</em> was proven; here the real scm <em>producer</em> is driven end
 * to end (real alert → real demand-planning suggestion → real approve → real
 * procurement PO → CONFIRMED) and a host {@link KafkaTestConsumer} asserts the
 * <b>byte-exact</b> {@code scm.procurement.inbound-expected.v1} envelope that wms
 * inbound-service consumes. The wms-consumer half (ASN creation, eventId /
 * {@code (poNumber,line)} dedup, unknown-warehouse fail-closed, 3PL reject,
 * cancel→CANCELLED) is proven deterministically on the wms side by
 * {@code ScmInboundExpectedConsumerIT} (real broker + real Postgres). The two
 * legs are linked by the identical canonical envelope, so together they close
 * the loop without inventing a new both-projects-in-one-JVM harness (ADR-050 §5:
 * the PR-gated deterministic legs are the authoritative guard; the nightly
 * federation live leg is the demonstration).
 *
 * <p>Cases observable on the producer side:
 * <ul>
 *   <li><b>happy / single warehouse</b> — one confirmed warehouse-addressed PO
 *       emits the envelope with CODE identifiers + decimal-string qty (smoke);</li>
 *   <li><b>multi-warehouse routing</b> — two SKUs seeded to two distinct
 *       warehouse codes → two envelopes carrying the two distinct codes (D3);</li>
 *   <li><b>single warehouse, two POs</b> — the degenerate case: two POs to the
 *       same warehouse code → two envelopes with that one code (D3);</li>
 *   <li><b>producer-side fail-closed</b> — an operator-authored PO (no warehouse
 *       destination) confirmed emits {@code po.confirmed} but <em>no</em>
 *       inbound-expected (D4 — never guess a warehouse);</li>
 *   <li><b>cancel</b> — a warehouse-addressed PO cancelled emits
 *       {@code inbound-expected.cancelled.v1} (D6.3).</li>
 * </ul>
 */
class InboundExpectedLoopE2ETest extends ScmPlatformE2ETestBase {

    private static final String TOPIC_INBOUND_EXPECTED = "scm.procurement.inbound-expected.v1";
    private static final String TOPIC_INBOUND_EXPECTED_CANCELLED =
            "scm.procurement.inbound-expected.cancelled.v1";
    private static final String TOPIC_PO_CONFIRMED = "scm.procurement.po.confirmed.v1";

    /** Default shared secret declared in procurement-service application.yml. */
    private static final String SUPPLIER_WEBHOOK_SECRET = "scm-supplier-webhook-secret";

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ------------------------------------------------------------------
    // AC-1 / AC-3 — happy path (single warehouse), full envelope (smoke)
    // ------------------------------------------------------------------

    @Test
    @Tag("smoke")
    @DisplayName("confirmed warehouse-addressed PO -> inbound-expected.v1 with CODE ids + decimal qty")
    void confirmedPoEmitsInboundExpectedWithCodes() throws Exception {
        String operator = jwt.signOperatorToken(randomAccountId());
        String sku = uniqueSku("SKU-IE");
        String warehouseCode = uniqueCode("WH-IE");
        String supplierCode = uniqueCode("SUP-IE");

        try (KafkaTestConsumer consumer = new KafkaTestConsumer(kafkaBootstrapForHost(),
                List.of(TOPIC_INBOUND_EXPECTED))) {
            seedMapping(operator, sku, supplierCode);
            seedPolicy(operator, sku, 10, 50);
            String poId = driveSuggestionPoToConfirmed(operator, sku, warehouseCode, randomLocationId());

            JsonNode payload = awaitInboundExpected(consumer, poId);
            assertThat(payload.get("poId").asText()).isEqualTo(poId);
            assertThat(payload.get("poNumber").asText()).isNotBlank();
            // ADR-050 D9 Option A — cross-service identifiers are CODES (not UUIDs).
            assertThat(payload.get("supplierId").asText())
                    .as("supplierId is the supplier CODE, verbatim").isEqualTo(supplierCode);
            assertThat(payload.get("destinationWarehouseId").asText())
                    .as("destinationWarehouseId is the warehouse CODE (addressed, not assumed)")
                    .isEqualTo(warehouseCode);
            assertThat(payload.get("destinationNodeType").asText()).isEqualTo("WMS_WAREHOUSE");
            assertThat(payload.get("expectedArrivalDate").asText())
                    .as("expectedArrivalDate is an ISO date").matches("\\d{4}-\\d{2}-\\d{2}");
            assertThat(payload.get("currency").asText()).isEqualTo("KRW");

            JsonNode lines = payload.get("lines");
            assertThat(lines.isArray()).isTrue();
            assertThat(lines).hasSize(1);
            JsonNode line0 = lines.get(0);
            assertThat(line0.get("skuCode").asText()).isEqualTo(sku);
            assertThat(line0.get("uom").asText()).isEqualTo("EA");
            // expectedQty is a plain DECIMAL STRING (ADR-050 D9), never a JSON number.
            JsonNode qty = line0.get("expectedQty");
            assertThat(qty.isTextual()).as("expectedQty is a decimal string").isTrue();
            assertThat(Integer.parseInt(qty.asText())).isPositive();
        }
    }

    // ------------------------------------------------------------------
    // AC-3 — multi-warehouse routing (two codes -> two events) (D3)
    // ------------------------------------------------------------------

    @Test
    @Tag("full")
    @DisplayName("two POs addressed to two warehouse codes -> two inbound-expected events carrying each code")
    void multiWarehouseRoutingEmitsDistinctCodes() throws Exception {
        String operator = jwt.signOperatorToken(randomAccountId());
        String skuA = uniqueSku("SKU-IE-A");
        String skuB = uniqueSku("SKU-IE-B");
        String warehouseA = uniqueCode("WH-IE-A");
        String warehouseB = uniqueCode("WH-IE-B");
        String supplierCode = uniqueCode("SUP-IE");

        try (KafkaTestConsumer consumer = new KafkaTestConsumer(kafkaBootstrapForHost(),
                List.of(TOPIC_INBOUND_EXPECTED))) {
            seedMapping(operator, skuA, supplierCode);
            seedPolicy(operator, skuA, 10, 50);
            seedMapping(operator, skuB, supplierCode);
            seedPolicy(operator, skuB, 10, 50);

            String poA = driveSuggestionPoToConfirmed(operator, skuA, warehouseA, randomLocationId());
            String poB = driveSuggestionPoToConfirmed(operator, skuB, warehouseB, randomLocationId());

            JsonNode payloadA = awaitInboundExpected(consumer, poA);
            JsonNode payloadB = awaitInboundExpected(consumer, poB);
            assertThat(payloadA.get("destinationWarehouseId").asText()).isEqualTo(warehouseA);
            assertThat(payloadB.get("destinationWarehouseId").asText()).isEqualTo(warehouseB);
            assertThat(payloadA.get("destinationWarehouseId").asText())
                    .as("distinct POs route to distinct warehouse codes, one code path")
                    .isNotEqualTo(payloadB.get("destinationWarehouseId").asText());
        }
    }

    // ------------------------------------------------------------------
    // AC-3 — single warehouse, two POs -> same code (degenerate D3)
    // ------------------------------------------------------------------

    @Test
    @Tag("full")
    @DisplayName("two POs to the same warehouse code -> two inbound-expected events with that one code")
    void singleWarehouseTwoPosShareOneCode() throws Exception {
        String operator = jwt.signOperatorToken(randomAccountId());
        String skuA = uniqueSku("SKU-IE-S1");
        String skuB = uniqueSku("SKU-IE-S2");
        String warehouseCode = uniqueCode("WH-IE-ONLY");
        String supplierCode = uniqueCode("SUP-IE");

        try (KafkaTestConsumer consumer = new KafkaTestConsumer(kafkaBootstrapForHost(),
                List.of(TOPIC_INBOUND_EXPECTED))) {
            seedMapping(operator, skuA, supplierCode);
            seedPolicy(operator, skuA, 10, 50);
            seedMapping(operator, skuB, supplierCode);
            seedPolicy(operator, skuB, 10, 50);

            String poA = driveSuggestionPoToConfirmed(operator, skuA, warehouseCode, randomLocationId());
            String poB = driveSuggestionPoToConfirmed(operator, skuB, warehouseCode, randomLocationId());

            JsonNode payloadA = awaitInboundExpected(consumer, poA);
            JsonNode payloadB = awaitInboundExpected(consumer, poB);
            assertThat(payloadA.get("destinationWarehouseId").asText()).isEqualTo(warehouseCode);
            assertThat(payloadB.get("destinationWarehouseId").asText()).isEqualTo(warehouseCode);
        }
    }

    // ------------------------------------------------------------------
    // AC-1 — producer-side fail-closed: no destination -> no inbound-expected (D4)
    // ------------------------------------------------------------------

    @Test
    @Tag("full")
    @DisplayName("operator-authored PO (no warehouse destination) confirmed -> po.confirmed but NO inbound-expected")
    void operatorAuthoredPoEmitsNoInboundExpected() throws Exception {
        String operator = jwt.signOperatorToken(randomAccountId());
        String supplierId = ProcurementDbFixtures.insertActiveSupplier(
                postgres, "scm", "Acme Supplier (e2e-ie-noemit)");
        String sku = uniqueSku("SKU-IE-NOEMIT");

        try (KafkaTestConsumer consumer = new KafkaTestConsumer(kafkaBootstrapForHost(),
                List.of(TOPIC_INBOUND_EXPECTED, TOPIC_PO_CONFIRMED))) {
            // Operator-authored draft carries NO destination warehouse (only the
            // from-suggestion path sets one) -> maybePublishInboundExpected skips it.
            String poId = draftOperatorPo(operator, supplierId, sku);
            submit(operator, poId);
            ack(poId);
            confirm(operator, poId);

            // Single accumulator so records drained while waiting for po.confirmed are
            // still inspected for a (wrongly) emitted inbound-expected — a two-list
            // split would let a drained inbound-expected slip past the later check.
            List<ConsumerRecord<String, String>> all = new ArrayList<>();
            // po.confirmed must arrive (proves the outbox flushed for this PO). Any
            // inbound-expected would be in the SAME outbox flush (same TX), so by now
            // it would already be on the broker if it were emitted.
            await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(500))
                    .untilAsserted(() -> {
                        all.addAll(consumer.drain());
                        assertThat(all.stream().anyMatch(
                                r -> TOPIC_PO_CONFIRMED.equals(r.topic()) && poId.equals(r.key())))
                                .as("po.confirmed for %s arrives", poId).isTrue();
                    });
            // Belt-and-suspenders: keep draining a short window for any late publish.
            long deadline = System.currentTimeMillis() + 5_000;
            while (System.currentTimeMillis() < deadline) {
                all.addAll(consumer.drain());
                Thread.sleep(300);
            }
            boolean anyInboundExpected = all.stream()
                    .anyMatch(r -> TOPIC_INBOUND_EXPECTED.equals(r.topic()) && poId.equals(r.key()));
            assertThat(anyInboundExpected)
                    .as("operator-authored PO (no warehouse) must NOT emit inbound-expected (D4)")
                    .isFalse();
        }
    }

    // ------------------------------------------------------------------
    // D6.3 — cancel a warehouse-addressed PO -> inbound-expected.cancelled.v1
    // ------------------------------------------------------------------

    @Test
    @Tag("full")
    @DisplayName("cancel a warehouse-addressed replenishment PO -> inbound-expected.cancelled.v1")
    void cancelWarehouseAddressedPoEmitsCancelled() throws Exception {
        String operator = jwt.signOperatorToken(randomAccountId());
        String sku = uniqueSku("SKU-IE-CXL");
        String warehouseCode = uniqueCode("WH-IE-CXL");
        String supplierCode = uniqueCode("SUP-IE");

        try (KafkaTestConsumer consumer = new KafkaTestConsumer(kafkaBootstrapForHost(),
                List.of(TOPIC_INBOUND_EXPECTED_CANCELLED))) {
            seedMapping(operator, sku, supplierCode);
            seedPolicy(operator, sku, 10, 50);
            // A from-suggestion PO is warehouse-addressed and still cancellable in DRAFT
            // (PoStatusMachine: OPERATOR DRAFT->CANCELED). v1 fires the companion cancel
            // event for any warehouse-addressed PO cancel (ADR-050 D6.3 wiring).
            String poId = driveSuggestionPoToDraft(operator, sku, warehouseCode, randomLocationId());
            cancel(operator, poId);

            JsonNode payload = awaitEnvelopePayload(consumer, TOPIC_INBOUND_EXPECTED_CANCELLED, poId);
            assertThat(payload.get("poId").asText()).isEqualTo(poId);
            assertThat(payload.get("poNumber").asText()).isNotBlank();
            assertThat(payload.get("lines").get(0).get("skuCode").asText()).isEqualTo(sku);
        }
    }

    // ==================================================================
    // Loop drivers
    // ==================================================================

    /** Drives alert -> suggestion -> approve, returning the (DRAFT) warehouse-addressed PO id. */
    private String driveSuggestionPoToDraft(String operator, String sku, String warehouseCode,
                                            String locationId) throws Exception {
        try (KafkaTestProducer producer = new KafkaTestProducer(kafkaBootstrapForHost())) {
            producer.publishLowStockAlert(UUID.randomUUID(), sku, locationId, warehouseCode, 3, 8);
        }
        String suggestionId = awaitSuggestion(operator, sku);
        return approve(operator, suggestionId);
    }

    /** Drives the full producer path to CONFIRMED for a warehouse-addressed PO. */
    private String driveSuggestionPoToConfirmed(String operator, String sku, String warehouseCode,
                                                String locationId) throws Exception {
        String poId = driveSuggestionPoToDraft(operator, sku, warehouseCode, locationId);
        submit(operator, poId);
        ack(poId);
        confirm(operator, poId);
        return poId;
    }

    // ==================================================================
    // REST helpers
    // ==================================================================

    private void seedMapping(String operator, String sku, String supplierCode) throws Exception {
        String body = "{\"supplierId\":\"" + supplierCode + "\",\"defaultOrderQty\":100,"
                + "\"leadTimeDays\":7,\"currency\":\"KRW\"}";
        HttpResponse<String> resp = sendString(http, authedJson(
                gatewayBaseUri().resolve(pathDemandPlanningSkuSupplierMap(sku)), operator)
                .PUT(HttpRequest.BodyPublishers.ofString(body)).build());
        assertThat(resp.statusCode()).as("PUT sku-supplier-map -> 200 (body: %s)", resp.body())
                .isEqualTo(200);
    }

    private void seedPolicy(String operator, String sku, int reorderPoint, int reorderQty)
            throws Exception {
        String body = "{\"reorderPoint\":" + reorderPoint + ",\"safetyStock\":5,"
                + "\"reorderQty\":" + reorderQty + "}";
        HttpResponse<String> resp = sendString(http, authedJson(
                gatewayBaseUri().resolve(pathDemandPlanningPolicy(sku)), operator)
                .PUT(HttpRequest.BodyPublishers.ofString(body)).build());
        assertThat(resp.statusCode()).as("PUT policy -> 200 (body: %s)", resp.body())
                .isEqualTo(200);
    }

    private String awaitSuggestion(String operator, String sku) {
        AtomicReference<String> id = new AtomicReference<>();
        await().atMost(Duration.ofSeconds(40)).pollInterval(Duration.ofMillis(750))
                .untilAsserted(() -> {
                    JsonNode found = suggestionInStatus(operator, sku);
                    assertThat(found).as("a SUGGESTED suggestion exists for %s", sku).isNotNull();
                    id.set(found.get("id").asText());
                });
        return id.get();
    }

    private JsonNode suggestionInStatus(String operator, String sku) throws Exception {
        URI uri = gatewayBaseUri().resolve(pathDemandPlanningSuggestions(sku));
        HttpResponse<String> resp = sendString(http, authedGet(uri, operator).GET().build());
        assertThat(resp.statusCode()).as("GET suggestions -> 200 (body: %s)", resp.body())
                .isEqualTo(200);
        for (JsonNode node : objectMapper.readTree(resp.body()).get("data")) {
            if ("SUGGESTED".equals(node.get("status").asText())) {
                return node;
            }
        }
        return null;
    }

    private String approve(String operator, String suggestionId) throws Exception {
        HttpResponse<String> resp = sendString(http, authedJson(
                gatewayBaseUri().resolve(pathDemandPlanningApprove(suggestionId)), operator)
                .POST(HttpRequest.BodyPublishers.ofString("{}")).build());
        assertThat(resp.statusCode()).as("approve -> 200 (body: %s)", resp.body()).isEqualTo(200);
        JsonNode data = objectMapper.readTree(resp.body()).get("data");
        assertThat(data.get("poStatus").asText()).isEqualTo("DRAFT");
        return data.get("poId").asText();
    }

    private String draftOperatorPo(String operator, String supplierId, String sku) throws Exception {
        String body = """
                {
                  "supplierId": "%s",
                  "currency": "KRW",
                  "lines": [
                    { "lineNo": 1, "sku": "%s", "supplierSku": "SUP-%s", "quantity": 12, "unitPrice": 3.00 }
                  ]
                }
                """.formatted(supplierId, sku, sku);
        HttpResponse<String> resp = sendString(http, authedJson(
                gatewayBaseUri().resolve(pathProcurementPo()), operator)
                .header("Idempotency-Key", uniqueIdempotencyKey())
                .POST(HttpRequest.BodyPublishers.ofString(body)).build());
        assertThat(resp.statusCode()).as("draft operator PO -> 201 (body: %s)", resp.body())
                .isEqualTo(201);
        return objectMapper.readTree(resp.body()).get("data").get("id").asText();
    }

    private void submit(String operator, String poId) throws Exception {
        supplierMock.enqueueSuccess("RCPT-IE-" + System.nanoTime());
        HttpResponse<String> resp = sendString(http, authedJson(
                gatewayBaseUri().resolve(pathProcurementPoSubmit(poId)), operator)
                .header("Idempotency-Key", uniqueIdempotencyKey())
                .POST(HttpRequest.BodyPublishers.noBody()).build());
        assertThat(resp.statusCode()).as("submit -> 200 (body: %s)", resp.body()).isEqualTo(200);
        assertThat(objectMapper.readTree(resp.body()).get("data").get("status").asText())
                .isEqualTo("SUBMITTED");
    }

    private void ack(String poId) throws Exception {
        String supplierAckRef = uniqueSupplierAckRef("IE-ACK");
        String body = """
                { "tenantId": "scm", "poId": "%s", "supplierAckRef": "%s" }
                """.formatted(poId, supplierAckRef);
        HttpResponse<String> resp = sendString(http, webhookSignedPost(
                procurementBaseUri().resolve(pathSupplierAckWebhook()),
                SUPPLIER_WEBHOOK_SECRET, body));
        assertThat(resp.statusCode()).as("supplier ack webhook -> 200 (body: %s)", resp.body())
                .isEqualTo(200);
        assertThat(objectMapper.readTree(resp.body()).get("data").get("status").asText())
                .isEqualTo("ACKNOWLEDGED");
    }

    private void confirm(String operator, String poId) throws Exception {
        HttpResponse<String> resp = sendString(http, authedJson(
                gatewayBaseUri().resolve(pathProcurementPoConfirm(poId)), operator)
                .header("Idempotency-Key", uniqueIdempotencyKey())
                .POST(HttpRequest.BodyPublishers.noBody()).build());
        assertThat(resp.statusCode()).as("confirm -> 200 (body: %s)", resp.body()).isEqualTo(200);
        assertThat(objectMapper.readTree(resp.body()).get("data").get("status").asText())
                .isEqualTo("CONFIRMED");
    }

    private void cancel(String operator, String poId) throws Exception {
        URI uri = gatewayBaseUri().resolve("/api/v1/procurement/po/" + poId + "/cancel");
        String body = "{\"reason\":\"e2e cancel — SCM-INT-004 D6.3\"}";
        HttpResponse<String> resp = sendString(http, authedJson(uri, operator)
                .header("Idempotency-Key", uniqueIdempotencyKey())
                .POST(HttpRequest.BodyPublishers.ofString(body)).build());
        assertThat(resp.statusCode()).as("cancel -> 200 (body: %s)", resp.body()).isEqualTo(200);
        assertThat(objectMapper.readTree(resp.body()).get("data").get("status").asText())
                .isEqualTo("CANCELED");
    }

    // ==================================================================
    // Kafka assertion helpers
    // ==================================================================

    private JsonNode awaitInboundExpected(KafkaTestConsumer consumer, String poId) {
        return awaitEnvelopePayload(consumer, TOPIC_INBOUND_EXPECTED, poId);
    }

    /** Awaits any envelope on {@code topic} keyed by {@code poId} and returns its full JSON. */
    private JsonNode awaitEnvelope(KafkaTestConsumer consumer, String topic, String poId) {
        AtomicReference<JsonNode> envelopeRef = new AtomicReference<>();
        List<ConsumerRecord<String, String>> seen = new ArrayList<>();
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    seen.addAll(consumer.drain());
                    ConsumerRecord<String, String> match = seen.stream()
                            .filter(r -> topic.equals(r.topic()))
                            .filter(r -> poId.equals(r.key()))
                            .findFirst().orElse(null);
                    assertThat(match).as("%s envelope keyed by PO id %s arrives", topic, poId).isNotNull();
                    envelopeRef.set(objectMapper.readTree(match.value()));
                });
        return envelopeRef.get();
    }

    private JsonNode awaitEnvelopePayload(KafkaTestConsumer consumer, String topic, String poId) {
        return awaitEnvelope(consumer, topic, poId).get("payload");
    }

    private static String uniqueCode(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
