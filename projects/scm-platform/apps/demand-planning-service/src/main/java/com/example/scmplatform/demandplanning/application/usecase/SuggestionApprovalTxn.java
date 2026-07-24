package com.example.scmplatform.demandplanning.application.usecase;

import com.example.scmplatform.demandplanning.application.port.outbound.ReorderSuggestionPort;
import com.example.scmplatform.demandplanning.application.port.outbound.SkuSupplierMappingPort;
import com.example.scmplatform.demandplanning.domain.error.InvalidSuggestionStateException;
import com.example.scmplatform.demandplanning.domain.error.SkuSupplierUnmappedException;
import com.example.scmplatform.demandplanning.domain.error.SuggestionNotFoundException;
import com.example.scmplatform.demandplanning.domain.model.ReorderSuggestion;
import com.example.scmplatform.demandplanning.domain.model.SkuSupplierMapping;
import com.example.scmplatform.demandplanning.domain.model.SuggestionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * The two transactional boundaries of the approve→materialize flow (ADR-MONO-027
 * D5), deliberately split so the cross-service procurement call sits
 * <em>between</em> two committed transactions rather than inside one:
 *
 * <ol>
 *   <li>{@link #prepareApprove(UUID)} — guard + resolve mapping + transition
 *       {@code SUGGESTED → APPROVED}, then <strong>commit</strong>. If procurement
 *       then fails, the suggestion stays {@code APPROVED} (architecture.md failure
 *       mode), and the operator's retry re-enters here as a no-op transition.</li>
 *   <li>{@link #completeMaterialize(UUID, String)} — after procurement returns a
 *       PO, transition {@code APPROVED → MATERIALIZED} with the linked poId.</li>
 * </ol>
 *
 * <p>A separate bean (not a self-call) so Spring's transactional proxy actually
 * applies to each method — the orchestrator {@link ApproveSuggestionUseCase}
 * invokes them through the proxy.
 */
@Component
@RequiredArgsConstructor
public class SuggestionApprovalTxn {

    private final ReorderSuggestionPort suggestionPort;
    private final SkuSupplierMappingPort mappingPort;

    /**
     * Tx-1: validate + resolve supplier mapping + move to APPROVED (committed).
     *
     * <ul>
     *   <li>MATERIALIZED → idempotent: returns the existing poId, no transition.</li>
     *   <li>DISMISSED → {@link InvalidSuggestionStateException} (422).</li>
     *   <li>unmapped SKU → {@link SkuSupplierUnmappedException} (422), thrown
     *       <em>before</em> any transition so the suggestion is unchanged.</li>
     *   <li>SUGGESTED → APPROVED (committed); already-APPROVED (a prior failed
     *       attempt) → no-op transition, proceeds to re-call procurement.</li>
     * </ul>
     */
    @Transactional
    public ApprovalPlan prepareApprove(UUID id) {
        ReorderSuggestion suggestion = suggestionPort.findById(id)
                .orElseThrow(() -> new SuggestionNotFoundException(id));

        if (suggestion.getStatus() == SuggestionStatus.MATERIALIZED) {
            return ApprovalPlan.alreadyMaterialized(suggestion.getMaterializedPoId());
        }
        if (suggestion.getStatus() == SuggestionStatus.DISMISSED) {
            throw new InvalidSuggestionStateException(
                    "Cannot approve a DISMISSED suggestion");
        }

        // Resolve the supplier mapping. Unmapped → 422; thrown before the
        // APPROVED transition so the suggestion stays SUGGESTED (AC-3).
        SkuSupplierMapping mapping = mappingPort.findBySkuCode(
                        suggestion.getTenantId(), suggestion.getSkuCode())
                .orElseThrow(() -> new SkuSupplierUnmappedException(suggestion.getSkuCode()));

        if (suggestion.getStatus() == SuggestionStatus.SUGGESTED) {
            suggestion.approve(Instant.now());
            suggestionPort.save(suggestion);
        }

        return ApprovalPlan.proceed(
                suggestion.getId(), mapping.getSupplierId(), mapping.getCurrency(),
                suggestion.getSkuCode(), suggestion.getSuggestedQty(),
                // ADR-MONO-050 D1/D3/D9: carry the seeding warehouse CODE + lead time so
                // procurement can address the wms inbound-expected event by CODE (Option A).
                // Null for BATCH suggestions (IVS carries no code) → procurement drafts
                // the PO but emits no inbound-expected (fail-closed).
                suggestion.getWarehouseCode(),
                // ADR-MONO-055 §D2/§D3: carry the destination node TYPE so the drafted PO is
                // addressed to the correct node type (WMS_WAREHOUSE or THIRD_PARTY_LOGISTICS).
                suggestion.getDestinationNodeType(), mapping.getLeadTimeDays());
    }

    /**
     * Tx-2: link the procurement DRAFT PO and move APPROVED → MATERIALIZED.
     * Idempotent: if the suggestion is already MATERIALIZED (a concurrent path
     * won), returns the existing poId without re-transitioning.
     */
    @Transactional
    public UUID completeMaterialize(UUID id, String poId) {
        ReorderSuggestion suggestion = suggestionPort.findById(id)
                .orElseThrow(() -> new SuggestionNotFoundException(id));
        if (suggestion.getStatus() == SuggestionStatus.MATERIALIZED) {
            return suggestion.getMaterializedPoId();
        }
        UUID poUuid = UUID.fromString(poId);
        suggestion.materialize(poUuid, Instant.now());
        suggestionPort.save(suggestion);
        return poUuid;
    }

    /**
     * Outcome of {@link #prepareApprove}: either the suggestion is already
     * materialized (short-circuit) or the resolved parameters for the procurement
     * call.
     */
    public record ApprovalPlan(boolean alreadyMaterialized, UUID existingPoId,
                               UUID suggestionId, String supplierId, String currency,
                               String skuCode, int quantity,
                               String warehouseCode, String destinationNodeType,
                               int leadTimeDays) {

        public static ApprovalPlan alreadyMaterialized(UUID poId) {
            return new ApprovalPlan(true, poId, null, null, null, null, 0, null, null, 0);
        }

        public static ApprovalPlan proceed(UUID suggestionId, String supplierId, String currency,
                                           String skuCode, int quantity,
                                           String warehouseCode, String destinationNodeType,
                                           int leadTimeDays) {
            return new ApprovalPlan(false, null, suggestionId, supplierId, currency,
                    skuCode, quantity, warehouseCode, destinationNodeType, leadTimeDays);
        }
    }
}
