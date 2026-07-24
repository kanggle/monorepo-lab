package com.example.scmplatform.demandplanning.application.usecase;

import com.example.scmplatform.demandplanning.application.port.outbound.InventoryVisibilityPort;
import com.example.scmplatform.demandplanning.application.port.outbound.ReorderPolicyPort;
import com.example.scmplatform.demandplanning.application.port.outbound.ReorderSuggestionPort;
import com.example.scmplatform.demandplanning.application.port.outbound.SkuSupplierMappingPort;
import com.example.scmplatform.demandplanning.domain.model.ReorderPolicy;
import com.example.scmplatform.demandplanning.domain.model.ReorderSuggestion;
import com.example.scmplatform.demandplanning.domain.model.SkuSupplierMapping;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Nightly batch sweep: reads the IVS inventory read-model for SKUs below their
 * reorder point without a fresh alert, and raises reorder suggestions through
 * the same open-suggestion guard as the live alert path (D6, S5).
 *
 * <p>If IVS is unavailable, the sweep skips the run and increments a metric;
 * the live alert path is unaffected (decoupled, S5).
 *
 * <p>Restartable / idempotent: re-running funnels through the open-suggestion guard,
 * so a re-run raises no duplicate.
 */
@Slf4j
@Service
public class SweepReorderUseCase {

    static final String TENANT_ID = "scm";

    private final ReorderPolicyPort policyPort;
    private final SkuSupplierMappingPort mappingPort;
    private final ReorderSuggestionPort suggestionPort;
    private final InventoryVisibilityPort ivsPort;

    private final Counter sweepSuggestionsCounter;
    private final Counter sweepSkippedUnmappedCounter;
    private final Counter sweepIvsUnavailableCounter;

    public SweepReorderUseCase(ReorderPolicyPort policyPort,
                                SkuSupplierMappingPort mappingPort,
                                ReorderSuggestionPort suggestionPort,
                                InventoryVisibilityPort ivsPort,
                                MeterRegistry meterRegistry) {
        this.policyPort = policyPort;
        this.mappingPort = mappingPort;
        this.suggestionPort = suggestionPort;
        this.ivsPort = ivsPort;

        this.sweepSuggestionsCounter = Counter.builder("reorder_suggestions_raised_total")
                .tag("source", "BATCH")
                .description("Total reorder suggestions raised from batch sweep")
                .register(meterRegistry);
        this.sweepSkippedUnmappedCounter = Counter.builder("reorder_sweep_skipped_unmapped_total")
                .description("Batch rows skipped due to unmapped SKU")
                .register(meterRegistry);
        this.sweepIvsUnavailableCounter = Counter.builder("reorder_sweep_ivs_unavailable_total")
                .description("Batch sweep runs skipped due to IVS unavailability")
                .register(meterRegistry);
    }

    /**
     * Execute the nightly sweep. Called by {@link com.example.scmplatform.demandplanning.adapter.outbound.batch.ReorderSweepScheduler}.
     */
    @Transactional
    public int sweep() {
        List<InventoryVisibilityPort.SkuWarehouseQty> candidates;
        try {
            candidates = ivsPort.findAllBelowReorderPoint(TENANT_ID);
        } catch (Exception e) {
            log.warn("IVS unavailable during sweep — skipping this run: {}", e.getMessage());
            sweepIvsUnavailableCounter.increment();
            return 0;
        }

        if (candidates.isEmpty()) {
            log.info("Batch sweep: no below-reorder-point candidates from IVS");
            return 0;
        }

        int raised = 0;
        Instant now = Instant.now();

        for (InventoryVisibilityPort.SkuWarehouseQty candidate : candidates) {
            String skuCode = candidate.skuCode();
            UUID warehouseId = candidate.warehouseId();
            int availableQty = candidate.availableQty();

            try {
                raised += evaluateBatchCandidate(skuCode, warehouseId, availableQty,
                        candidate.warehouseCode(), candidate.nodeType(), now);
            } catch (Exception e) {
                log.warn("Batch sweep: error evaluating skuCode={} warehouseId={}: {}",
                        skuCode, warehouseId, e.getMessage());
                // Don't abort the sweep for a single failed candidate
            }
        }

        log.info("Batch sweep complete: raised={} total candidates={}", raised, candidates.size());
        return raised;
    }

    /**
     * @param warehouseCode ADR-MONO-050 D9 / TASK-SCM-BE-037 — the IVS node's business
     *                      warehouse code, threaded onto the suggestion so a batch-origin
     *                      PO addresses its wms inbound-expected by code. Nullable; a null
     *                      never suppresses the suggestion (fail-closed addressing only).
     * @param nodeType      ADR-MONO-055 §D2/§D3 / TASK-SCM-BE-048 — the IVS node's type,
     *                      threaded onto the suggestion so a below-reorder
     *                      {@code THIRD_PARTY_LOGISTICS} node drafts a PO addressed to that
     *                      3PL node. Nullable; a null normalises to {@code WMS_WAREHOUSE}
     *                      in the suggestion (backward compat). The reorder policy itself is
     *                      unchanged — only the target vocabulary widens (ADR-055 §D2).
     */
    private int evaluateBatchCandidate(String skuCode, UUID warehouseId, int availableQty,
                                       String warehouseCode, String nodeType, Instant now) {
        // Resolve mapping — skip unmapped SKU (logged, not DLT'd in batch — no event to route)
        Optional<SkuSupplierMapping> mappingOpt = mappingPort.findBySkuCode(TENANT_ID, skuCode);
        if (mappingOpt.isEmpty()) {
            log.warn("Batch sweep: unmapped SKU skipped: skuCode={} warehouseId={}", skuCode, warehouseId);
            sweepSkippedUnmappedCounter.increment();
            return 0;
        }
        SkuSupplierMapping mapping = mappingOpt.get();

        // Evaluate reorder policy
        Optional<ReorderPolicy> policyOpt = policyPort.findBySkuCode(TENANT_ID, skuCode);
        int reorderPoint;
        int reorderQty;
        if (policyOpt.isPresent()) {
            ReorderPolicy policy = policyOpt.get();
            reorderPoint = policy.getReorderPoint();
            reorderQty = policy.getReorderQty();
        } else {
            // Degraded fallback: use availableQty as threshold (will always trigger) + default_order_qty
            log.warn("Batch sweep: no reorder policy for skuCode={}, using defaultOrderQty fallback", skuCode);
            reorderPoint = availableQty; // treat current qty as threshold → will trigger
            reorderQty = mapping.getDefaultOrderQty();
        }

        if (availableQty > reorderPoint) {
            return 0;
        }

        // D6: open-suggestion guard (same as live path)
        if (suggestionPort.hasOpenSuggestion(TENANT_ID, skuCode, warehouseId)) {
            log.debug("Batch sweep: open-suggestion guard hit for skuCode={} warehouseId={}; skipping",
                    skuCode, warehouseId);
            return 0;
        }

        ReorderSuggestion suggestion = ReorderSuggestion.raiseFromBatch(
                UUID.randomUUID(), skuCode, warehouseId, warehouseCode, nodeType,
                mapping.getSupplierId(), reorderQty, availableQty, TENANT_ID, now);

        suggestionPort.save(suggestion);
        sweepSuggestionsCounter.increment();

        log.info("Batch sweep: reorder suggestion raised: id={} skuCode={} warehouseId={} qty={} availableQty={}",
                suggestion.getId(), skuCode, warehouseId, reorderQty, availableQty);
        return 1;
    }
}
