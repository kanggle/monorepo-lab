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
    static final UUID SUPPLIER_ID = UUID.randomUUID();

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
        SkuSupplierMappingJpaEntity m = new SkuSupplierMappingJpaEntity();
        m.setTenantId(TENANT_SCM);
        m.setSkuCode(SKU);
        m.setSupplierId(SUPPLIER_ID);
        m.setDefaultOrderQty(100);
        m.setLeadTimeDays(7);
        m.setCurrency("KRW");
        mappingJpa.save(m);
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
        assertThat(body.path("supplierId").asText()).isEqualTo(SUPPLIER_ID.toString());
        assertThat(body.path("lines").get(0).path("sku").asText()).isEqualTo(SKU);
        assertThat(body.path("lines").get(0).path("quantity").asInt()).isEqualTo(100);
        // ADR-MONO-050 D1/D3/D4: the seeding warehouse + lead time + node type
        // are addressed on the from-suggestion body.
        assertThat(body.path("destinationWarehouseId").asText()).isEqualTo(WAREHOUSE_ID.toString());
        assertThat(body.path("destinationNodeType").asText()).isEqualTo("WMS_WAREHOUSE");
        assertThat(body.path("leadTimeDays").asInt()).isEqualTo(7);
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
