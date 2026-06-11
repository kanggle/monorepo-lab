package com.example.scmplatform.demandplanning.integration;

import com.example.scmplatform.demandplanning.adapter.outbound.batch.ReorderSweepScheduler;
import com.example.scmplatform.demandplanning.adapter.outbound.persistence.jpa.ReorderPolicyJpaEntity;
import com.example.scmplatform.demandplanning.adapter.outbound.persistence.jpa.ReorderSuggestionJpaEntity;
import com.example.scmplatform.demandplanning.adapter.outbound.persistence.jpa.SkuSupplierMappingJpaEntity;
import com.example.scmplatform.demandplanning.domain.model.SuggestionSource;
import com.example.scmplatform.demandplanning.domain.model.SuggestionStatus;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * IT: nightly batch sweep reads the live IVS read-model and raises BATCH-source
 * reorder suggestions for below-reorder-point SKUs (TASK-SCM-BE-026, ADR-MONO-027
 * §D7 / §D7.1). IVS is a MockWebServer stub of the internal endpoint
 * {@code GET /internal/inventory-visibility/snapshot}; the
 * {@code InventoryVisibilityRestAdapter} + {@code SweepReorderUseCase} +
 * {@code ReorderSweepScheduler} are exercised end-to-end against real Postgres.
 *
 * <ul>
 *   <li>AC-2: below-reorder SKU in the snapshot → one BATCH suggestion.</li>
 *   <li>above reorder point → no suggestion.</li>
 *   <li>AC-3: re-run funnels through the open-suggestion guard → no duplicate.</li>
 *   <li>AC-4: IVS unavailable (5xx) → sweep skips the run, raises 0, never throws.</li>
 * </ul>
 */
@DisplayName("IT: batch sweep → IVS internal read → BATCH suggestion (ADR-027 §D7.1)")
class SweepSchedulerIntegrationTest extends AbstractDemandPlanningIntegrationTest {

    private static MockWebServer ivsMock;

    static final String SKU = "SKU-SWEEP-IT";
    static final UUID WAREHOUSE_ID = UUID.randomUUID();
    static final UUID SUPPLIER_ID = UUID.randomUUID();

    // One MockWebServer for the class — the adapter binds its base-url at bean
    // construction, so the port must stay stable. Each test enqueues exactly the
    // responses its sweeps consume (one GET per runSweep()).
    @DynamicPropertySource
    static void ivsMockUrl(DynamicPropertyRegistry registry) throws IOException {
        ivsMock = new MockWebServer();
        ivsMock.start();
        registry.add("scmplatform.demand-planning.inventory-visibility.base-url",
                () -> "http://" + ivsMock.getHostName() + ":" + ivsMock.getPort());
    }

    @AfterAll
    static void shutdownMock() throws IOException {
        if (ivsMock != null) {
            ivsMock.shutdown();
        }
    }

    @Autowired
    ReorderSweepScheduler sweepScheduler;

    @BeforeEach
    void seedPolicyAndMapping() {
        SkuSupplierMappingJpaEntity mapping = new SkuSupplierMappingJpaEntity();
        mapping.setTenantId(TENANT_SCM);
        mapping.setSkuCode(SKU);
        mapping.setSupplierId(SUPPLIER_ID);
        mapping.setDefaultOrderQty(100);
        mapping.setLeadTimeDays(5);
        mapping.setCurrency("USD");
        mappingJpa.save(mapping);

        ReorderPolicyJpaEntity policy = new ReorderPolicyJpaEntity();
        policy.setTenantId(TENANT_SCM);
        policy.setSkuCode(SKU);
        policy.setReorderPoint(20);
        policy.setSafetyStock(5);
        policy.setReorderQty(50);
        policy.setVersion(0);
        policy.setUpdatedAt(Instant.now());
        policyJpa.save(policy);
    }

    /** Enqueue one IVS internal-snapshot response (the envelope the controller returns). */
    private void enqueueSnapshot(String sku, UUID nodeId, int availableQty) {
        String body = "{\"data\":[{\"sku\":\"" + sku + "\",\"nodeId\":\"" + nodeId
                + "\",\"availableQty\":" + availableQty + "}],\"meta\":{\"count\":1}}";
        ivsMock.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(body));
    }

    @Test
    @DisplayName("below reorder point in IVS snapshot → one BATCH suggestion")
    void sweep_belowReorderPoint_raisesBatchSuggestion() {
        enqueueSnapshot(SKU, WAREHOUSE_ID, 5); // 5 <= reorderPoint(20)

        sweepScheduler.runSweep();

        List<ReorderSuggestionJpaEntity> all = suggestionJpa.findAll();
        assertThat(all).hasSize(1);
        ReorderSuggestionJpaEntity s = all.get(0);
        assertThat(s.getSkuCode()).isEqualTo(SKU);
        assertThat(s.getWarehouseId()).isEqualTo(WAREHOUSE_ID);
        assertThat(s.getStatus()).isEqualTo(SuggestionStatus.SUGGESTED);
        assertThat(s.getSource()).isEqualTo(SuggestionSource.BATCH);
        assertThat(s.getSuggestedQty()).isEqualTo(50); // reorderQty
        assertThat(s.getSupplierId()).isEqualTo(SUPPLIER_ID);
    }

    @Test
    @DisplayName("above reorder point → no suggestion")
    void sweep_aboveReorderPoint_raisesNothing() {
        enqueueSnapshot(SKU, WAREHOUSE_ID, 50); // 50 > reorderPoint(20)

        sweepScheduler.runSweep();

        assertThat(suggestionJpa.findAll()).isEmpty();
    }

    @Test
    @DisplayName("re-run funnels through the open-suggestion guard → no duplicate (AC-3)")
    void sweep_idempotent_openGuard() {
        enqueueSnapshot(SKU, WAREHOUSE_ID, 5);
        enqueueSnapshot(SKU, WAREHOUSE_ID, 5);

        sweepScheduler.runSweep();
        sweepScheduler.runSweep();

        long suggested = suggestionJpa.findAll().stream()
                .filter(s -> s.getStatus() == SuggestionStatus.SUGGESTED)
                .count();
        assertThat(suggested).isEqualTo(1);
    }

    @Test
    @DisplayName("IVS unavailable (5xx) → sweep skips the run, raises 0, never throws (AC-4)")
    void sweep_ivsUnavailable_skipsRun_noThrow() {
        ivsMock.enqueue(new MockResponse().setResponseCode(503));

        assertThatCode(() -> sweepScheduler.runSweep()).doesNotThrowAnyException();
        assertThat(suggestionJpa.findAll()).isEmpty();
    }
}
