package com.example.scmplatform.demandplanning.integration;

import com.example.scmplatform.demandplanning.adapter.outbound.persistence.jpa.ReorderSuggestionJpaEntity;
import com.example.scmplatform.demandplanning.adapter.outbound.persistence.jpa.SkuSupplierMappingJpaEntity;
import com.example.scmplatform.demandplanning.application.usecase.ApproveSuggestionUseCase;
import com.example.scmplatform.demandplanning.domain.error.SkuSupplierUnmappedException;
import com.example.scmplatform.demandplanning.domain.model.SuggestionSource;
import com.example.scmplatform.demandplanning.domain.model.SuggestionStatus;
import com.fasterxml.jackson.databind.JsonNode;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * IT: operator approve → procurement DRAFT-PO materialization (ADR-MONO-027 D5 /
 * TASK-SCM-BE-025). procurement is a MockWebServer stub; the cross-service call,
 * state transition, and idempotency are exercised end-to-end against real
 * Postgres.
 *
 * <ul>
 *   <li>AC-1: approve → procurement called with {@code origin=DEMAND_PLANNING} +
 *       {@code sourceSuggestionId}; suggestion → MATERIALIZED with poId.</li>
 *   <li>AC-2: re-approve → no second procurement request (idempotent short-circuit),
 *       same poId.</li>
 *   <li>AC-3: unmapped SKU → 422, suggestion stays SUGGESTED, procurement never
 *       called.</li>
 * </ul>
 */
@DisplayName("IT: approve → procurement DRAFT-PO materialization (ADR-027 D5)")
class ApproveMaterializationIntegrationTest extends AbstractDemandPlanningIntegrationTest {

    private static MockWebServer procurementMock;

    static final String SKU = "SKU-APPLE-APPROVE-IT";
    static final UUID WAREHOUSE_ID = UUID.randomUUID();
    // ADR-MONO-050 D9: warehouse + supplier CODES flow to procurement (Option A).
    static final String WAREHOUSE_CODE = "WH-SEOUL-01";
    static final String SUPPLIER_ID = "SUP-0043";

    // One MockWebServer for the whole class — the client binds its base-url
    // (host:port) once at bean construction, so the port must stay stable. Each
    // test enqueues exactly the responses it triggers and drains its own requests
    // with a timeout (no cross-test counter coupling).
    @DynamicPropertySource
    static void procurementMockUrl(DynamicPropertyRegistry registry) throws IOException {
        procurementMock = new MockWebServer();
        procurementMock.start();
        registry.add("scmplatform.demand-planning.procurement.base-url",
                () -> "http://" + procurementMock.getHostName() + ":" + procurementMock.getPort());
    }

    @AfterAll
    static void shutdownMock() throws IOException {
        if (procurementMock != null) {
            procurementMock.shutdown();
        }
    }

    @Autowired
    ApproveSuggestionUseCase approveUseCase;

    private UUID seedSuggestion() {
        UUID id = UUID.randomUUID();
        ReorderSuggestionJpaEntity s = new ReorderSuggestionJpaEntity();
        s.setId(id);
        s.setSkuCode(SKU);
        s.setWarehouseId(WAREHOUSE_ID);
        s.setWarehouseCode(WAREHOUSE_CODE);
        s.setSupplierId(SUPPLIER_ID);
        s.setSuggestedQty(100);
        s.setStatus(SuggestionStatus.SUGGESTED);
        s.setSource(SuggestionSource.ALERT);
        s.setTriggerEventId(UUID.randomUUID());
        s.setTriggerAvailableQty(5);
        s.setTenantId(TENANT_SCM);
        s.setVersion(0);
        s.setCreatedAt(Instant.now());
        s.setUpdatedAt(Instant.now());
        suggestionJpa.save(s);
        return id;
    }

    private void seedMapping() {
        seedMapping(SKU);
    }

    private void seedMapping(String skuCode) {
        SkuSupplierMappingJpaEntity m = new SkuSupplierMappingJpaEntity();
        m.setTenantId(TENANT_SCM);
        m.setSkuCode(skuCode);
        m.setSupplierId(SUPPLIER_ID);
        m.setDefaultOrderQty(100);
        m.setLeadTimeDays(7);
        m.setCurrency("KRW");
        mappingJpa.save(m);
    }

    // ADR-MONO-055 §D2/§D3: a BATCH-sourced suggestion targeting a THIRD_PARTY_LOGISTICS
    // node — no warehouse code (wms-only), node type THIRD_PARTY_LOGISTICS.
    static final String SKU_3PL = "SKU-APPLE-3PL-IT";

    private UUID seed3plSuggestion() {
        UUID id = UUID.randomUUID();
        ReorderSuggestionJpaEntity s = new ReorderSuggestionJpaEntity();
        s.setId(id);
        s.setSkuCode(SKU_3PL);
        s.setWarehouseId(UUID.randomUUID());
        s.setWarehouseCode(null); // a 3PL node carries no wms warehouse code
        s.setDestinationNodeType("THIRD_PARTY_LOGISTICS");
        s.setSupplierId(SUPPLIER_ID);
        s.setSuggestedQty(100);
        s.setStatus(SuggestionStatus.SUGGESTED);
        s.setSource(SuggestionSource.BATCH);
        s.setTriggerEventId(null); // BATCH has no trigger event
        s.setTriggerAvailableQty(2);
        s.setTenantId(TENANT_SCM);
        s.setVersion(0);
        s.setCreatedAt(Instant.now());
        s.setUpdatedAt(Instant.now());
        suggestionJpa.save(s);
        return id;
    }

    private void enqueueDraftPo(String poId) {
        procurementMock.enqueue(new MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"data\":{\"id\":\"" + poId + "\",\"status\":\"DRAFT\"},\"meta\":{}}"));
    }

    @Test
    @DisplayName("AC-1: approve materializes a DRAFT PO and carries provenance to procurement")
    void approveMaterializesDraftPo() throws Exception {
        UUID suggestionId = seedSuggestion();
        seedMapping();
        String poId = UUID.randomUUID().toString();
        enqueueDraftPo(poId);

        ApproveSuggestionUseCase.ApproveResult result =
                approveUseCase.approve(suggestionId, "Bearer operator-token");

        assertThat(result.status()).isEqualTo(SuggestionStatus.MATERIALIZED);
        assertThat(result.poId()).hasToString(poId);
        assertThat(result.poStatus()).isEqualTo("DRAFT");

        // Suggestion persisted as MATERIALIZED with the linked PO.
        ReorderSuggestionJpaEntity persisted = suggestionJpa.findById(suggestionId).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(SuggestionStatus.MATERIALIZED);
        assertThat(persisted.getMaterializedPoId()).hasToString(poId);

        // The procurement request carried origin + sourceSuggestionId + the line.
        RecordedRequest request = procurementMock.takeRequest(2, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getPath()).isEqualTo("/api/procurement/po/from-suggestion");
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer operator-token");
        JsonNode body = objectMapper.readTree(request.getBody().readUtf8());
        assertThat(body.path("origin").asText()).isEqualTo("DEMAND_PLANNING");
        assertThat(body.path("sourceSuggestionId").asText()).isEqualTo(suggestionId.toString());
        assertThat(body.path("currency").asText()).isEqualTo("KRW");
        // ADR-MONO-050 D9: supplierId is emitted as the supplier CODE, verbatim.
        assertThat(body.path("supplierId").asText()).isEqualTo(SUPPLIER_ID);
        assertThat(body.path("lines").get(0).path("sku").asText()).isEqualTo(SKU);
        assertThat(body.path("lines").get(0).path("quantity").asInt()).isEqualTo(100);
        // ADR-MONO-050 D1/D3/D4/D9: the seeding warehouse CODE + lead time + node type
        // are addressed on the from-suggestion body (destination resolved by CODE).
        assertThat(body.path("destinationWarehouseId").asText()).isEqualTo(WAREHOUSE_CODE);
        assertThat(body.path("destinationNodeType").asText()).isEqualTo("WMS_WAREHOUSE");
        assertThat(body.path("leadTimeDays").asInt()).isEqualTo(7);
    }

    @Test
    @DisplayName("ADR-055 §D2: a BATCH 3PL suggestion drafts a PO with destinationNodeType="
            + "THIRD_PARTY_LOGISTICS and no destinationWarehouseId")
    void approve3plBatchSuggestion_addressesPoToThirdPartyLogisticsNode() throws Exception {
        UUID suggestionId = seed3plSuggestion();
        seedMapping(SKU_3PL);
        String poId = UUID.randomUUID().toString();
        enqueueDraftPo(poId);

        ApproveSuggestionUseCase.ApproveResult result =
                approveUseCase.approve(suggestionId, "Bearer operator-token");

        assertThat(result.status()).isEqualTo(SuggestionStatus.MATERIALIZED);

        RecordedRequest request = procurementMock.takeRequest(2, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        JsonNode body = objectMapper.readTree(request.getBody().readUtf8());
        // The allocation half (ADR-055 §D2): the PO is addressed to the 3PL node type.
        assertThat(body.path("destinationNodeType").asText()).isEqualTo("THIRD_PARTY_LOGISTICS");
        // A 3PL node carries no warehouse code, so no destinationWarehouseId is emitted —
        // procurement's emit-gate correctly skips the wms inbound-expected (BE-049 sink).
        assertThat(body.path("destinationWarehouseId").isMissingNode()
                || body.path("destinationWarehouseId").isNull()).isTrue();
        assertThat(body.path("lines").get(0).path("sku").asText()).isEqualTo(SKU_3PL);
    }

    @Test
    @DisplayName("AC-2: re-approve is idempotent — no second procurement call, same poId")
    void reApproveIsIdempotent() throws Exception {
        UUID suggestionId = seedSuggestion();
        seedMapping();
        String poId = UUID.randomUUID().toString();
        enqueueDraftPo(poId);

        ApproveSuggestionUseCase.ApproveResult first =
                approveUseCase.approve(suggestionId, "Bearer t");
        ApproveSuggestionUseCase.ApproveResult second =
                approveUseCase.approve(suggestionId, "Bearer t");

        assertThat(second.poId()).isEqualTo(first.poId());
        // The first approve hit procurement exactly once; the second
        // short-circuited (no further request reaches the mock).
        RecordedRequest firstReq = procurementMock.takeRequest(2, TimeUnit.SECONDS);
        assertThat(firstReq).isNotNull();
        RecordedRequest noSecondReq = procurementMock.takeRequest(500, TimeUnit.MILLISECONDS);
        assertThat(noSecondReq)
                .as("re-approve must not call procurement again")
                .isNull();
    }

    @Test
    @DisplayName("AC-3: unmapped SKU → 422, suggestion stays SUGGESTED, procurement untouched")
    void unmappedSkuLeavesSuggestionUntouched() throws InterruptedException {
        UUID suggestionId = seedSuggestion();
        // No mapping seeded → unmapped.

        assertThatThrownBy(() -> approveUseCase.approve(suggestionId, "Bearer t"))
                .isInstanceOf(SkuSupplierUnmappedException.class);

        ReorderSuggestionJpaEntity persisted = suggestionJpa.findById(suggestionId).orElseThrow();
        assertThat(persisted.getStatus())
                .as("suggestion stays SUGGESTED when the SKU is unmapped")
                .isEqualTo(SuggestionStatus.SUGGESTED);
        RecordedRequest noReq = procurementMock.takeRequest(500, TimeUnit.MILLISECONDS);
        assertThat(noReq)
                .as("procurement must never be called for an unmapped SKU")
                .isNull();
    }
}
