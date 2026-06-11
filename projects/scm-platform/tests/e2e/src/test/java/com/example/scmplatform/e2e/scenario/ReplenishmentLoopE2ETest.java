package com.example.scmplatform.e2e.scenario;

import static com.example.scmplatform.e2e.testsupport.E2ETestFixtures.authedGet;
import static com.example.scmplatform.e2e.testsupport.E2ETestFixtures.authedJson;
import static com.example.scmplatform.e2e.testsupport.E2ETestFixtures.pathDemandPlanningApprove;
import static com.example.scmplatform.e2e.testsupport.E2ETestFixtures.pathDemandPlanningPolicy;
import static com.example.scmplatform.e2e.testsupport.E2ETestFixtures.pathDemandPlanningSkuSupplierMap;
import static com.example.scmplatform.e2e.testsupport.E2ETestFixtures.pathDemandPlanningSuggestions;
import static com.example.scmplatform.e2e.testsupport.E2ETestFixtures.pathProcurementPoById;
import static com.example.scmplatform.e2e.testsupport.E2ETestFixtures.randomAccountId;
import static com.example.scmplatform.e2e.testsupport.E2ETestFixtures.randomLocationId;
import static com.example.scmplatform.e2e.testsupport.E2ETestFixtures.sendString;
import static com.example.scmplatform.e2e.testsupport.E2ETestFixtures.uniqueSku;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.scmplatform.e2e.testsupport.KafkaTestProducer;
import com.example.scmplatform.e2e.testsupport.ScmPlatformE2ETestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Replenishment-loop cross-service E2E (TASK-SCM-INT-002 leg 1, ADR-MONO-027).
 *
 * <p>Deterministic, PR-gated proof of the full in-project loop — a simulated wms
 * low-stock alert flows through demand-planning into a procurement DRAFT PO,
 * with no wms container booted (the host {@link KafkaTestProducer} plays wms,
 * emitting the exact {@code inventory.low-stock-detected} envelope so this stays
 * a faithful proxy for the federation live leg).
 *
 * <pre>
 *   PUT sku-supplier-map + reorder policy (operator seed)
 *     → publish wms.inventory.alert.v1 (availableQty &lt; reorderPoint)
 *       → demand-planning raises a SUGGESTED suggestion
 *         → operator POST /approve
 *           → procurement DRAFT PO (origin=DEMAND_PLANNING, sourceSuggestionId)
 * </pre>
 *
 * <p>Smoke = the happy path (AC-1). Full = idempotency (AC-2), unmapped→no
 * suggestion (AC-3), and open-guard re-suggest after MATERIALIZED (AC-4).
 */
class ReplenishmentLoopE2ETest extends ScmPlatformE2ETestBase {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ------------------------------------------------------------------
    // AC-1 — happy path (PR-gated)
    // ------------------------------------------------------------------

    @Test
    @Tag("smoke")
    @DisplayName("wms low-stock alert -> demand-planning suggestion -> approve -> procurement DRAFT PO")
    void replenishmentHappyPath() throws Exception {
        String operator = jwt.signOperatorToken(randomAccountId());
        String sku = uniqueSku("SKU-REPLEN");
        String warehouseId = randomLocationId();
        UUID supplierId = UUID.randomUUID();

        seedMapping(operator, sku, supplierId);
        seedPolicy(operator, sku, 10, 50);

        try (KafkaTestProducer producer = new KafkaTestProducer(kafkaBootstrapForHost())) {
            // availableQty(3) < reorderPoint(10) -> a suggestion must be raised.
            producer.publishLowStockAlert(UUID.randomUUID(), sku, warehouseId, 3, 8);
        }

        String suggestionId = awaitSuggestion(operator, sku, "SUGGESTED");

        // ----- operator approves -> procurement DRAFT PO -------------------
        HttpResponse<String> approveResp = sendString(http, authedJson(
                gatewayBaseUri().resolve(pathDemandPlanningApprove(suggestionId)), operator)
                .POST(HttpRequest.BodyPublishers.ofString("{}")).build());
        assertThat(approveResp.statusCode())
                .as("approve -> 200 (body: %s)", approveResp.body())
                .isEqualTo(200);
        JsonNode approveData = objectMapper.readTree(approveResp.body()).get("data");
        assertThat(approveData.get("status").asText()).isEqualTo("MATERIALIZED");
        assertThat(approveData.get("poStatus").asText()).isEqualTo("DRAFT");
        String poId = approveData.get("poId").asText();
        assertThat(poId).isNotBlank();

        // ----- the procurement DRAFT PO carries the provenance -------------
        HttpResponse<String> poResp = sendString(http, authedGet(
                gatewayBaseUri().resolve(pathProcurementPoById(poId)), operator).GET().build());
        assertThat(poResp.statusCode()).as("GET procurement PO -> 200").isEqualTo(200);
        JsonNode po = objectMapper.readTree(poResp.body()).get("data");
        assertThat(po.get("status").asText()).as("PO is DRAFT only — never auto-SUBMITted").isEqualTo("DRAFT");
        assertThat(po.get("origin").asText()).isEqualTo("DEMAND_PLANNING");
        assertThat(po.get("sourceSuggestionId").asText()).isEqualTo(suggestionId);
    }

    // ------------------------------------------------------------------
    // AC-2 — idempotency (dedup alert + re-approve)
    // ------------------------------------------------------------------

    @Test
    @Tag("full")
    @DisplayName("duplicate alert eventId -> one suggestion; re-approve -> one PO (idempotent)")
    void idempotentAlertAndApprove() throws Exception {
        String operator = jwt.signOperatorToken(randomAccountId());
        String sku = uniqueSku("SKU-REPLEN-IDEM");
        String warehouseId = randomLocationId();
        seedMapping(operator, sku, UUID.randomUUID());
        seedPolicy(operator, sku, 10, 50);

        UUID eventId = UUID.randomUUID();
        try (KafkaTestProducer producer = new KafkaTestProducer(kafkaBootstrapForHost())) {
            producer.publishLowStockAlert(eventId, sku, warehouseId, 2, 8);
            String suggestionId = awaitSuggestion(operator, sku, "SUGGESTED");
            // Re-publish the SAME eventId — T8 dedup must not raise a second suggestion.
            producer.publishLowStockAlert(eventId, sku, warehouseId, 2, 8);
            Thread.sleep(3_000);
            assertThat(listSuggestions(operator, sku).size())
                    .as("duplicate eventId must not raise a second suggestion (T8)")
                    .isEqualTo(1);

            // Re-approve idempotency: two approves -> same poId, no duplicate PO.
            String firstPo = approve(operator, suggestionId);
            String secondPo = approve(operator, suggestionId);
            assertThat(secondPo).as("re-approve returns the same PO (idempotent)").isEqualTo(firstPo);
        }
    }

    // ------------------------------------------------------------------
    // AC-3 — unmapped SKU raises no suggestion (fail-closed)
    // ------------------------------------------------------------------

    @Test
    @Tag("full")
    @DisplayName("unmapped SKU alert -> DLT, no suggestion raised (fail-closed)")
    void unmappedSkuRaisesNoSuggestion() throws Exception {
        String operator = jwt.signOperatorToken(randomAccountId());
        String sku = uniqueSku("SKU-REPLEN-UNMAPPED");
        String warehouseId = randomLocationId();
        // Deliberately seed NO mapping.

        try (KafkaTestProducer producer = new KafkaTestProducer(kafkaBootstrapForHost())) {
            producer.publishLowStockAlert(UUID.randomUUID(), sku, warehouseId, 1, 8);
        }
        // The consumer routes the unmapped SKU to the DLT immediately; give it a
        // moment and assert no suggestion materialized.
        Thread.sleep(5_000);
        assertThat(listSuggestions(operator, sku))
                .as("unmapped SKU must not raise a suggestion (fail-closed DLT)")
                .isEmpty();
    }

    // ------------------------------------------------------------------
    // AC-4 — open-guard releases after MATERIALIZED
    // ------------------------------------------------------------------

    @Test
    @Tag("full")
    @DisplayName("after a suggestion MATERIALIZED, a fresh below-threshold alert raises a new suggestion")
    void openGuardReleasedAfterMaterialized() throws Exception {
        String operator = jwt.signOperatorToken(randomAccountId());
        String sku = uniqueSku("SKU-REPLEN-GUARD");
        String warehouseId = randomLocationId();
        seedMapping(operator, sku, UUID.randomUUID());
        seedPolicy(operator, sku, 10, 50);

        try (KafkaTestProducer producer = new KafkaTestProducer(kafkaBootstrapForHost())) {
            producer.publishLowStockAlert(UUID.randomUUID(), sku, warehouseId, 2, 8);
            String first = awaitSuggestion(operator, sku, "SUGGESTED");
            approve(operator, first); // -> MATERIALIZED, releases the open guard

            // A second below-threshold alert (new eventId) for the same SKU+warehouse
            // must raise a FRESH suggestion now that the prior one is terminal (D6).
            producer.publishLowStockAlert(UUID.randomUUID(), sku, warehouseId, 1, 8);
            await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofSeconds(1))
                    .untilAsserted(() -> assertThat(suggestionInStatus(operator, sku, "SUGGESTED"))
                            .as("open-guard released -> a new SUGGESTED suggestion exists")
                            .isNotNull());
        }
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private void seedMapping(String operator, String sku, UUID supplierId) throws Exception {
        String body = "{\"supplierId\":\"" + supplierId + "\",\"defaultOrderQty\":100,"
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

    /** Awaits the first suggestion for {@code sku} in {@code status}, returns its id. */
    private String awaitSuggestion(String operator, String sku, String status) {
        AtomicReference<String> id = new AtomicReference<>();
        await().atMost(Duration.ofSeconds(40)).pollInterval(Duration.ofMillis(750))
                .untilAsserted(() -> {
                    JsonNode found = suggestionInStatus(operator, sku, status);
                    assertThat(found).as("a %s suggestion exists for %s", status, sku).isNotNull();
                    id.set(found.get("id").asText());
                });
        return id.get();
    }

    /** Returns the first suggestion node for {@code sku} in {@code status}, or null. */
    private JsonNode suggestionInStatus(String operator, String sku, String status) throws Exception {
        for (JsonNode node : listSuggestions(operator, sku)) {
            if (status.equals(node.get("status").asText())) {
                return node;
            }
        }
        return null;
    }

    /** Lists suggestions for {@code sku} via the gateway; returns the data array. */
    private JsonNode listSuggestions(String operator, String sku) throws Exception {
        URI uri = gatewayBaseUri().resolve(pathDemandPlanningSuggestions(sku));
        HttpResponse<String> resp = sendString(http, authedGet(uri, operator).GET().build());
        assertThat(resp.statusCode()).as("GET suggestions -> 200 (body: %s)", resp.body())
                .isEqualTo(200);
        return objectMapper.readTree(resp.body()).get("data");
    }

    /** Approves {@code suggestionId}; returns the linked poId. */
    private String approve(String operator, String suggestionId) throws Exception {
        HttpResponse<String> resp = sendString(http, authedJson(
                gatewayBaseUri().resolve(pathDemandPlanningApprove(suggestionId)), operator)
                .POST(HttpRequest.BodyPublishers.ofString("{}")).build());
        assertThat(resp.statusCode()).as("approve -> 200 (body: %s)", resp.body()).isEqualTo(200);
        return objectMapper.readTree(resp.body()).get("data").get("poId").asText();
    }
}
