package com.example.erp.approval.domain.route;

import com.example.erp.approval.domain.error.ApprovalErrors.ApprovalRouteInvalidException;

import java.util.Objects;

/**
 * Single-stage approval route (first increment): exactly one {@link Approver}
 * (E3, architecture.md § Approver authorization). Multi-stage routing (1~N) and
 * delegation (대결/위임) are v2-deferred — NOT modelled here.
 *
 * <p>A route is malformed (→ {@code APPROVAL_ROUTE_INVALID}) when it has no
 * approver, or when the approver equals the submitter (self-approval — E3/I4).
 * Pure module — no framework imports.
 */
public record ApprovalRoute(Approver approver) {

    public ApprovalRoute {
        Objects.requireNonNull(approver, "approver");
    }

    /**
     * Construct + validate a single-stage route. Refuses a missing approver or
     * a self-approving route ({@code submitterId == approverId}).
     */
    public static ApprovalRoute singleStage(String submitterId, String approverId) {
        if (approverId == null || approverId.isBlank()) {
            throw new ApprovalRouteInvalidException(
                    "route is malformed: approver is required");
        }
        SelfApprovalGuard.ensureNotSelfApproval(submitterId, approverId);
        return new ApprovalRoute(new Approver(approverId));
    }

    public String approverId() {
        return approver.approverId();
    }

    public boolean isApprover(String principalId) {
        return approver.matches(principalId);
    }
}
