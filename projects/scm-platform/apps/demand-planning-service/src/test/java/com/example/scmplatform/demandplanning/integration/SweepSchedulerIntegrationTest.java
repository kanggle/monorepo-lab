package com.example.scmplatform.demandplanning.integration;

import com.example.scmplatform.demandplanning.adapter.outbound.persistence.jpa.ReorderPolicyJpaEntity;
import com.example.scmplatform.demandplanning.adapter.outbound.persistence.jpa.ReorderSuggestionJpaEntity;
import com.example.scmplatform.demandplanning.adapter.outbound.persistence.jpa.SkuSupplierMappingJpaEntity;
import com.example.scmplatform.demandplanning.application.usecase.SweepReorderUseCase;
import com.example.scmplatform.demandplanning.domain.model.SuggestionSource;
import com.example.scmplatform.demandplanning.domain.model.SuggestionStatus;
import okhttp3.mockwebserver.Dispatcher;
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
 * <p>Determinism:
 * <ul>
 *   <li>Calls {@link SweepReorderUseCase#sweep()} DIRECTLY rather than the
 *       ShedLock-wrapped {@code ReorderSweepScheduler.runSweep()}. The scheduler
 *       lock is {@code lockAtLeastFor=PT5M}, so the FIRST runSweep in the JVM holds
 *       the lock for 5 minutes and every later call (other tests, and this test's
 *       second sweep) is a silent no-op — which is exactly what the open-guard
 *       re-run case needs to actually execute.</li>
 *   <li>The IVS stub uses a {@link Dispatcher} (not FIFO enqueue) so the response is
 *       independent of request count.</li>
 *   <li>Each test uses a <b>unique SKU + warehouse</b> and asserts only on that SKU,
 *       so the shared Kafka consumer re-processing leaked alerts from sibling IT
 *       classes (earliest-offset group) cannot perturb the count.</li>
 * </ul>
 */
@DisplayName("IT: batch sweep → IVS internal read → BATCH suggestion (ADR-027 §D7.1)")
class SweepSchedulerIntegrationTest extends AbstractDemandPlanningIntegrationTest {

    private static MockWebServer ivsMock;

    // Dispatcher state — set per test before triggering the sweep.
    private static volatile boolean ivsUnavailable = false;
    private static volatile String ivsSku = "";
    private static volatile String ivsNodeId = "";
    private static volatile int ivsQty = 0;

    @DynamicPropertySource
    static void ivsMockUrl(DynamicPropertyRegistry registry) throws IOException {
        ivsMock = new MockWebServer();
        ivsMock.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                if (ivsUnavailable) {
                    return new MockResponse().setResponseCode(503);
                }
                String body = "{\"data\":[{\"sku\":\"" + ivsSku + "\",\"nodeId\":\"" + ivsNodeId
                        + "\",\"availableQty\":" + ivsQty + "}],\"meta\":{\"count\":1}}";
                return new MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(body);
            }
        });
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
    SweepReorderUseCase sweepUseCase;

    /** A fresh, collision-proof SKU + warehouse per test (open-guard is keyed on both). */
    private String uniqueSku() {
        return "SKU-SWEEP-IT-" + UUID.randomUUID();
    }

    private void seedPolicyAndMapping(String sku, UUID supplierId, int reorderPoint, int reorderQty) {
        SkuSupplierMappingJpaEntity mapping = new SkuSupplierMappingJpaEntity();
        mapping.setTenantId(TENANT_SCM);
        mapping.setSkuCode(sku);
        mapping.setSupplierId(supplierId);
        mapping.setDefaultOrderQty(100);
        mapping.setLeadTimeDays(5);
        mapping.setCurrency("USD");
        mappingJpa.save(mapping);

        ReorderPolicyJpaEntity policy = new ReorderPolicyJpaEntity();
        policy.setTenantId(TENANT_SCM);
        policy.setSkuCode(sku);
        policy.setReorderPoint(reorderPoint);
        policy.setSafetyStock(5);
        policy.setReorderQty(reorderQty);
        policy.setVersion(0);
        policy.setUpdatedAt(Instant.now());
        policyJpa.save(policy);
    }

    /** Point the IVS stub at one snapshot row for {@code sku}@{@code nodeId} with {@code qty}. */
    private void expectSnapshot(String sku, UUID nodeId, int qty) {
        ivsUnavailable = false;
        ivsSku = sku;
        ivsNodeId = nodeId.toString();
        ivsQty = qty;
    }

    private void expectIvsUnavailable() {
        ivsUnavailable = true;
    }

    private List<ReorderSuggestionJpaEntity> suggestionsFor(String sku) {
        return suggestionJpa.findAll().stream()
                .filter(s -> sku.equals(s.getSkuCode()))
                .toList();
    }

    @Test
    @DisplayName("below reorder point in IVS snapshot → one BATCH suggestion")
    void sweep_belowReorderPoint_raisesBatchSuggestion() {
        String sku = uniqueSku();
        UUID warehouse = UUID.randomUUID();
        UUID supplier = UUID.randomUUID();
        seedPolicyAndMapping(sku, supplier, 20, 50);
        expectSnapshot(sku, warehouse, 5); // 5 <= reorderPoint(20)

        sweepUseCase.sweep();

        List<ReorderSuggestionJpaEntity> mine = suggestionsFor(sku);
        assertThat(mine).hasSize(1);
        ReorderSuggestionJpaEntity s = mine.get(0);
        assertThat(s.getWarehouseId()).isEqualTo(warehouse);
        assertThat(s.getStatus()).isEqualTo(SuggestionStatus.SUGGESTED);
        assertThat(s.getSource()).isEqualTo(SuggestionSource.BATCH);
        assertThat(s.getSuggestedQty()).isEqualTo(50); // reorderQty
        assertThat(s.getSupplierId()).isEqualTo(supplier);
    }

    @Test
    @DisplayName("above reorder point → no suggestion")
    void sweep_aboveReorderPoint_raisesNothing() {
        String sku = uniqueSku();
        UUID warehouse = UUID.randomUUID();
        seedPolicyAndMapping(sku, UUID.randomUUID(), 20, 50);
        expectSnapshot(sku, warehouse, 50); // 50 > reorderPoint(20)

        sweepUseCase.sweep();

        assertThat(suggestionsFor(sku)).isEmpty();
    }

    @Test
    @DisplayName("re-run funnels through the open-suggestion guard → no duplicate (AC-3)")
    void sweep_idempotent_openGuard() {
        String sku = uniqueSku();
        UUID warehouse = UUID.randomUUID();
        seedPolicyAndMapping(sku, UUID.randomUUID(), 20, 50);
        expectSnapshot(sku, warehouse, 5);

        sweepUseCase.sweep();
        sweepUseCase.sweep();

        long suggested = suggestionsFor(sku).stream()
                .filter(s -> s.getStatus() == SuggestionStatus.SUGGESTED)
                .count();
        assertThat(suggested).isEqualTo(1);
    }

    @Test
    @DisplayName("IVS unavailable (5xx) → sweep skips the run, raises 0, never throws (AC-4)")
    void sweep_ivsUnavailable_skipsRun_noThrow() {
        String sku = uniqueSku();
        UUID warehouse = UUID.randomUUID();
        seedPolicyAndMapping(sku, UUID.randomUUID(), 20, 50);
        expectIvsUnavailable();

        assertThatCode(() -> sweepUseCase.sweep()).doesNotThrowAnyException();
        assertThat(suggestionsFor(sku)).isEmpty();
    }
}
