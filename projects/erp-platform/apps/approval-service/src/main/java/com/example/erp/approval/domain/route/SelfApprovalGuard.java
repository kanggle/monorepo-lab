package com.example.erp.approval.domain.route;

import com.example.erp.approval.domain.error.ApprovalErrors.ApprovalRouteInvalidException;

/**
 * Separation-of-Duties guard (E3 / I4): the submitter MUST NOT be the route's
 * approver. Structurally forbids self-approval. Pure module — no framework
 * imports.
 *
 * <p>Evaluated at route construction (submit time); a violation →
 * {@code APPROVAL_ROUTE_INVALID} ({@code details.cause = "self_approval"}).
 * The approver-eligibility check at approve/reject time is the independent
 * second SoD gate (request ≠ approve).
 */
public final class SelfApprovalGuard {

    private SelfApprovalGuard() {
    }

    public static void ensureNotSelfApproval(String submitterId, String approverId) {
        if (submitterId != null && submitterId.equals(approverId)) {
            throw new ApprovalRouteInvalidException(
                    "self-approval is forbidden: submitter '" + submitterId
                            + "' must not be the approver (E3/I4 Separation of Duties)");
        }
    }
}
