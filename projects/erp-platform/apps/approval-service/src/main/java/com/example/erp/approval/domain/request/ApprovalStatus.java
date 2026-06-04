package com.example.erp.approval.domain.request;

/**
 * Approval request lifecycle status (erp E3, architecture.md § State Machine).
 * First-increment single-stage machine:
 * {@code DRAFT → SUBMITTED → APPROVED | REJECTED | WITHDRAWN}. The three decision
 * states are terminal/finalized. The {@code IN_REVIEW} intermediate + multi-stage
 * routing are v2-deferred (NOT modelled here).
 *
 * <p>Pure Java — no framework imports.
 */
public enum ApprovalStatus {
    DRAFT,
    SUBMITTED,
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
