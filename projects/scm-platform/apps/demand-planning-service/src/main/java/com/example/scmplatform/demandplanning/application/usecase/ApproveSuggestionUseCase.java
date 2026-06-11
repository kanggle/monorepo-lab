package com.example.scmplatform.demandplanning.application.usecase;

import com.example.scmplatform.demandplanning.application.port.outbound.ProcurementDraftPoPort;
import com.example.scmplatform.demandplanning.domain.model.SuggestionStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Approve a reorder suggestion and materialize it into a procurement DRAFT PO
 * (ADR-MONO-027 D5).
 *
 * <p>Orchestrates two committed transactions ({@link SuggestionApprovalTxn})
 * around one synchronous, idempotent, compensation-free procurement call:
 *
 * <pre>
 *   prepareApprove (Tx-1, commit: SUGGESTED→APPROVED)
 *        └─ procurement.createDraftFromSuggestion (idempotent on sourceSuggestionId)
 *             └─ completeMaterialize (Tx-2, commit: APPROVED→MATERIALIZED + poId)
 * </pre>
 *
 * <p>If the procurement call fails between the two transactions, the suggestion
 * stays {@code APPROVED}; the operator retries and the call's idempotency
 * guarantees no duplicate PO (architecture.md failure mode; ADR-027 D5 Cat-none
 * single-call, no saga).
 *
 * <p>Not {@code @Transactional} itself — it must <em>not</em> hold a transaction
 * open across the remote call (that would defeat the two-commit design).
 */
@Slf4j
@Service
public class ApproveSuggestionUseCase {

    private final SuggestionApprovalTxn approvalTxn;
    private final ProcurementDraftPoPort procurementPort;

    private final Counter draftPoCreatedCounter;
    private final Counter draftPoFailuresCounter;

    public ApproveSuggestionUseCase(SuggestionApprovalTxn approvalTxn,
                                    ProcurementDraftPoPort procurementPort,
                                    MeterRegistry meterRegistry) {
        this.approvalTxn = approvalTxn;
        this.procurementPort = procurementPort;
        this.draftPoCreatedCounter = Counter.builder("reorder_draft_po_created_total")
                .description("Reorder suggestions materialized into a procurement DRAFT PO")
                .register(meterRegistry);
        this.draftPoFailuresCounter = Counter.builder("reorder_draft_po_failures_total")
                .description("Approve attempts where the procurement DRAFT-PO call failed")
                .register(meterRegistry);
    }

    /**
     * @param id          suggestion id
     * @param bearerToken the operator's {@code Authorization} header, propagated
     *                    to procurement (intra-scm trust); may be {@code null}.
     */
    public ApproveResult approve(UUID id, String bearerToken) {
        SuggestionApprovalTxn.ApprovalPlan plan = approvalTxn.prepareApprove(id);

        // Idempotent short-circuit: already materialized → return the linked PO
        // without re-calling procurement (does not depend on procurement uptime).
        if (plan.alreadyMaterialized()) {
            log.info("Approve idempotent hit: suggestion={} already MATERIALIZED poId={}",
                    id, plan.existingPoId());
            return new ApproveResult(id, SuggestionStatus.MATERIALIZED,
                    plan.existingPoId(), PO_STATUS_DRAFT);
        }

        ProcurementDraftPoPort.DraftPoResult po;
        try {
            po = procurementPort.createDraftFromSuggestion(
                    new ProcurementDraftPoPort.DraftPoCommand(
                            plan.suggestionId(), plan.supplierId(), plan.currency(),
                            plan.skuCode(), plan.quantity()),
                    bearerToken);
        } catch (RuntimeException e) {
            // Suggestion is left APPROVED (Tx-1 committed); operator retries.
            draftPoFailuresCounter.increment();
            throw e;
        }

        UUID poId = approvalTxn.completeMaterialize(id, po.poId());
        draftPoCreatedCounter.increment();
        String poStatus = po.poStatus() != null ? po.poStatus() : PO_STATUS_DRAFT;
        log.info("Suggestion materialized: id={} poId={} poStatus={}", id, poId, poStatus);
        return new ApproveResult(id, SuggestionStatus.MATERIALIZED, poId, poStatus);
    }

    private static final String PO_STATUS_DRAFT = "DRAFT";

    /**
     * Result of an approve: the suggestion id + its (now MATERIALIZED) status,
     * the linked procurement poId, and the PO's status (always DRAFT — never
     * auto-SUBMITted).
     */
    public record ApproveResult(UUID suggestionId, SuggestionStatus status,
                                UUID poId, String poStatus) {
    }
}
