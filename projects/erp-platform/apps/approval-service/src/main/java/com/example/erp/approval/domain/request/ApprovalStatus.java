package com.example.erp.approval.domain.request;

/**
 * Approval request lifecycle status (erp E3, architecture.md § State Machine).
 * v2.0 multi-stage machine:
 * {@code DRAFT → SUBMITTED → (IN_REVIEW →) APPROVED | REJECTED | WITHDRAWN}.
 * {@code APPROVED}, {@code REJECTED}, {@code WITHDRAWN} are terminal/finalized;
 * {@code IN_REVIEW} (TASK-ERP-BE-012) is a NON-terminal intermediate reached when
 * a non-final stage of a multi-stage route is approved (the request advances to
 * the next stage). A single-stage (N=1) route never passes through
 * {@code IN_REVIEW} (the strict v1.0 backward-compatible subset).
 *
 * <p>Pure Java — no framework imports.
 */
public enum ApprovalStatus {
    DRAFT,
    SUBMITTED,
    IN_REVIEW,
    APPROVED,
    REJECTED,
    WITHDRAWN;

    /**
     * A finalized request is immutable — any further transition command →
     * {@code APPROVAL_ALREADY_FINALIZED} (E3 / E4). {@code APPROVED},
     * {@code REJECTED}, {@code WITHDRAWN} are terminal.
     */
    public boolean isFinalized() {
        return this == APPROVED || this == REJECTED || this == WITHDRAWN;
    }
}
