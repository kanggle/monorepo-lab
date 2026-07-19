package com.example.scmplatform.demandplanning.application.usecase;

import com.example.scmplatform.demandplanning.application.port.outbound.OpsAlertPort;
import com.example.scmplatform.demandplanning.application.port.outbound.ProcessedEventPort;
import com.example.scmplatform.demandplanning.application.port.outbound.ReorderPolicyPort;
import com.example.scmplatform.demandplanning.application.port.outbound.ReorderSuggestionPort;
import com.example.scmplatform.demandplanning.application.port.outbound.SkuSupplierMappingPort;
import com.example.scmplatform.demandplanning.domain.error.SkuSupplierUnmappedException;
import com.example.scmplatform.demandplanning.domain.model.ReorderPolicy;
import com.example.scmplatform.demandplanning.domain.model.ReorderSuggestion;
import com.example.scmplatform.demandplanning.domain.model.SkuSupplierMapping;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Evaluates the scm reorder policy against an incoming alert (or batch row) and
 * raises a {@link ReorderSuggestion} if needed.
 *
 * <p>v1 rule (reorder-policy.md):
 * <pre>
 *   policy = reorder_policy[skuCode]  (fallback: use sku_supplier_map.default_order_qty)
 *   if availableQty <= policy.reorderPoint: raise suggestion
 *   else: no-op
 * </pre>
 *
 * <p>Business-dedup: open-suggestion guard (D6) — if a SUGGESTED or APPROVED
 * suggestion already exists for (tenantId, skuCode, warehouseId), skip.
 *
 * <p>Idempotency: event dedup on eventId (T8) — duplicate eventId = no-op.
 *
 * <p>Fail-closed: unmapped skuCode → {@link SkuSupplierUnmappedException} (non-retryable DLT).
 */
@Slf4j
@Service
public class EvaluateReorderUseCase {

    static final String TOPIC_ALERT = "wms.inventory.alert.v1";
    static final String TENANT_ID = "scm";

    private final ReorderPolicyPort policyPort;
    private final SkuSupplierMappingPort mappingPort;
    private final ReorderSuggestionPort suggestionPort;
    private final ProcessedEventPort processedEventPort;
    private final OpsAlertPort opsAlertPort;

    private final Counter suggestionsRaisedAlertCounter;
    private final Counter dedupHitsCounter;
    private final Counter openGuardHitsCounter;
    private final Counter unmappedSkuCounter;

    public EvaluateReorderUseCase(ReorderPolicyPort policyPort,
                                   SkuSupplierMappingPort mappingPort,
                                   ReorderSuggestionPort suggestionPort,
                                   ProcessedEventPort processedEventPort,
                                   OpsAlertPort opsAlertPort,
                                   MeterRegistry meterRegistry) {
        this.policyPort = policyPort;
        this.mappingPort = mappingPort;
        this.suggestionPort = suggestionPort;
        this.processedEventPort = processedEventPort;
        this.opsAlertPort = opsAlertPort;

        this.suggestionsRaisedAlertCounter = Counter.builder("reorder_suggestions_raised_total")
                .tag("source", "ALERT")
                .description("Total reorder suggestions raised from alert")
                .register(meterRegistry);
        this.dedupHitsCounter = Counter.builder("reorder_alert_dedup_hits_total")
                .description("Duplicate alert events skipped by T8 dedup")
                .register(meterRegistry);
        this.openGuardHitsCounter = Counter.builder("reorder_open_guard_hits_total")
                .description("Alert events skipped by open-suggestion guard")
                .register(meterRegistry);
        this.unmappedSkuCounter = Counter.builder("reorder_suggestion_unmapped_sku_total")
                .description("Alert events with unmapped SKU (non-retryable DLT)")
                .register(meterRegistry);
    }

    /**
     * Process a wms low-stock alert. Called by {@code WmsLowStockAlertConsumer}.
     *
     * @param eventId        envelope eventId (T8 idempotency key)
     * @param skuCode        SKU code (join key to policy + mapping)
     * @param warehouseId    warehouse dimension of the suggestion key (dedup key)
     * @param warehouseCode  warehouse CODE (ADR-050 D9) → flows to the PO destination
     * @param availableQty   available quantity at alert time
     * @param alertThreshold wms alert threshold (informational only — scm uses its own reorder_point)
     * @param occurredAt     event occurrence time
     */
    @Transactional
    public void evaluateFromAlert(UUID eventId, String skuCode, UUID warehouseId, String warehouseCode,
                                   int availableQty, int alertThreshold, Instant occurredAt) {
        // T8: event dedup
        if (processedEventPort.isDuplicate(eventId)) {
            log.debug("Duplicate alert event skipped: eventId={} skuCode={}", eventId, skuCode);
            dedupHitsCounter.increment();
            return;
        }

        // Fail-closed: unmapped SKU → non-retryable DLT + ops alert
        SkuSupplierMapping mapping = mappingPort.findBySkuCode(TENANT_ID, skuCode)
                .orElseThrow(() -> {
                    log.error("Unmapped SKU on alert: skuCode={} eventId={}", skuCode, eventId);
                    unmappedSkuCounter.increment();
                    opsAlertPort.alertUnmappedSku(skuCode, eventId.toString(), TOPIC_ALERT);
                    return new SkuSupplierUnmappedException(skuCode);
                });

        // Evaluate reorder policy (D4)
        Optional<ReorderPolicy> policyOpt = policyPort.findBySkuCode(TENANT_ID, skuCode);
        int reorderPoint;
        int reorderQty;
        if (policyOpt.isPresent()) {
            ReorderPolicy policy = policyOpt.get();
            reorderPoint = policy.getReorderPoint();
            reorderQty = policy.getReorderQty();
        } else {
            // Fallback: use wms alert threshold as reorder_point and mapping.default_order_qty as qty
            log.warn("No reorder policy for skuCode={}, degraded fallback: using alertThreshold={} defaultOrderQty={}",
                    skuCode, alertThreshold, mapping.getDefaultOrderQty());
            reorderPoint = alertThreshold;
            reorderQty = mapping.getDefaultOrderQty();
        }

        if (availableQty > reorderPoint) {
            log.debug("Alert skipped: availableQty={} > reorderPoint={} skuCode={} warehouseId={}",
                    availableQty, reorderPoint, skuCode, warehouseId);
            // Mark event processed even on no-op so re-delivery doesn't re-evaluate
            processedEventPort.markProcessed(eventId, TENANT_ID, Instant.now(), TOPIC_ALERT);
            return;
        }

        // D6: open-suggestion guard
        if (suggestionPort.hasOpenSuggestion(TENANT_ID, skuCode, warehouseId)) {
            log.debug("Open-suggestion guard: suggestion already open for skuCode={} warehouseId={}; skipping",
                    skuCode, warehouseId);
            openGuardHitsCounter.increment();
            processedEventPort.markProcessed(eventId, TENANT_ID, Instant.now(), TOPIC_ALERT);
            return;
        }

        Instant now = Instant.now();
        ReorderSuggestion suggestion = ReorderSuggestion.raiseFromAlert(
                UUID.randomUUID(), skuCode, warehouseId, warehouseCode, mapping.getSupplierId(),
                reorderQty, eventId, availableQty, TENANT_ID, now);

        suggestionPort.save(suggestion);
        processedEventPort.markProcessed(eventId, TENANT_ID, now, TOPIC_ALERT);
        suggestionsRaisedAlertCounter.increment();

        log.info("Reorder suggestion raised from ALERT: id={} skuCode={} warehouseId={} qty={} availableQty={}",
                suggestion.getId(), skuCode, warehouseId, reorderQty, availableQty);
    }
}
