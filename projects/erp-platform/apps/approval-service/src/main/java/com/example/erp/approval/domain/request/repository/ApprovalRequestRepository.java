package com.example.erp.approval.domain.request.repository;

import com.example.erp.approval.domain.request.ApprovalAction;
import com.example.erp.approval.domain.request.ApprovalRequest;
import com.example.erp.approval.domain.request.ApprovalStatus;
import com.example.erp.approval.domain.route.ApprovalRoute;
import com.example.erp.approval.domain.route.ApprovalRouteStage;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for the {@link ApprovalRequest} aggregate + its transition
 * {@link ApprovalAction} history (architecture.md § Layer Structure). Every
 * signature carries {@code tenantId} (defense-in-depth — no cross-tenant
 * repository method). The persistence adapter uses {@code saveAndFlush} for
 * transitions so the optimistic-lock conflict surfaces inside the use-case Tx
 * (BE-335 lesson).
 */
public interface ApprovalRequestRepository {

    ApprovalRequest save(ApprovalRequest request);

    /** Flush-on-save so optimistic-lock + constraint failures surface in-Tx. */
    ApprovalRequest saveAndFlush(ApprovalRequest request);

    Optional<ApprovalRequest> findById(String id, String tenantId);

    List<ApprovalRequest> findAll(String tenantId, ApprovalStatus status, int page, int size);

    /** Requests where the actor is the submitter OR the approver (scope-aware list). */
    List<ApprovalRequest> findByParticipant(String tenantId, String participantId,
                                            ApprovalStatus status, int page, int size);

    /** Pending inbox: SUBMITTED requests whose approver equals {@code approverId}. */
    List<ApprovalRequest> findInbox(String tenantId, String approverId, int page, int size);

    ApprovalAction appendAction(ApprovalAction action);

    List<ApprovalAction> findActions(String approvalRequestId, String tenantId);

    // ---- multi-stage route (TASK-ERP-BE-012) ----

    /** Persist the ordered stage rows for a request (create time). */
    List<ApprovalRouteStage> saveStages(List<ApprovalRouteStage> stages);

    /** The ordered (by stage_index) persisted stages of a request. */
    List<ApprovalRouteStage> findStages(String requestId, String tenantId);

    /**
     * Load + rehydrate the {@link ApprovalRoute} of a request from its persisted
     * stages (ordered). Throws if no stages exist (a malformed/legacy request
     * without a backfilled stage row).
     */
    ApprovalRoute loadRoute(String requestId, String tenantId);
}
