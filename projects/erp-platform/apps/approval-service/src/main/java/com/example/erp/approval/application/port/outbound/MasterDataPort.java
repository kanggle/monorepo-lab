package com.example.erp.approval.application.port.outbound;

import com.example.erp.approval.domain.request.ApprovalSubject;

/**
 * Outbound port for the submit-time subject reference-integrity check (E1,
 * architecture.md § Reference Integrity model). A synchronous REST call to
 * {@code masterdata-service} (ADR-MONO-005 Category B) verifies the referenced
 * master subject EXISTS and is ACTIVE before a request may leave DRAFT.
 *
 * <p>approval-service holds NO master data and never writes it back — the master
 * is reached only through this port (no shared-table JOIN even though both live
 * in the same MySQL instance).
 */
public interface MasterDataPort {

    /**
     * @param tenantId the tenant the submit use case is acting for. The adapter checks it
     *                 against the identity it propagates downstream and refuses the call on
     *                 a mismatch (TASK-ERP-BE-041) — it is a consistency input, not a
     *                 request parameter.
     * @return {@code true} iff the subject resolves to an EXISTING + ACTIVE
     *         master of its type. {@code false} for not-found / RETIRED /
     *         unreachable masterdata (the submit use case maps {@code false}
     *         to {@code APPROVAL_ROUTE_INVALID} — the request stays DRAFT,
     *         never advanced against a dangling master reference).
     *
     *         <p><strong>{@code false} is not a single outcome.</strong> "The master is
     *         absent or RETIRED" and "we could not ask" collapse to the same boolean here
     *         because the contract's refusal is the same either way, but they are
     *         <em>separated in the adapter's observability</em>: only the second increments
     *         {@code approval_subject_resolve_failures_total{cause}} and logs at WARN. An
     *         implementation that folds an authentication failure into a silent
     *         {@code false} reports a customer-data problem for an infrastructure one —
     *         that was TASK-ERP-BE-041.
     */
    boolean isSubjectActive(ApprovalSubject subject, String tenantId);

    /**
     * The employee linked to the caller's own {@code sub} — masterdata
     * {@code GET /api/erp/masterdata/employees/me} with the caller's token propagated
     * (TASK-MONO-776, approval-api.md § v2.4 «호출자의 직원»). {@code NOT_FOUND} = the caller's
     * account is linked to no employee in this tenant (an answer, not a failure). A
     * {@code RETIRED} employee is returned as FOUND — the use case decides.
     *
     * @param callerSub the use case's caller ({@code ActorContext.actorId()}). Like
     *                  {@code tenantId}, a consistency input: the adapter refuses the call when
     *                  it differs from the propagated token's {@code sub}, so «whose employee did
     *                  we look up» can never silently be someone else's.
     */
    EmployeeLookup callerEmployee(String callerSub, String tenantId);

    /**
     * Approval-line resolution view of an employee — masterdata
     * {@code GET /api/erp/masterdata/employees/{id}/approver-ref} (no department data scope:
     * an approver normally sits outside the submitter's scope). Used for the submit-time E3
     * check of every stage approver and for the {@code delegateId} check.
     */
    EmployeeLookup approverRef(String employeeId, String tenantId);
}
