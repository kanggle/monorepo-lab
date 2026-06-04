package com.example.auth.application.port;

import java.util.List;

/**
 * TASK-BE-327 (ADR-MONO-020 § 3.3 step 2, D2) — port for the issuance-time
 * assume-tenant authorization check against admin-service. Implementation lives
 * in {@code infrastructure/client/AdminAssignmentClient}.
 *
 * <p><b>fail-CLOSED contract</b>: {@link #resolveAssignment(String, String)}
 * returns ONLY when admin-service positively confirms the operator's D1
 * assignment to the selected tenant. ANY failure — not-assigned, admin-service
 * down, timeout, 4xx/5xx, circuit-open, IO — surfaces as
 * {@link com.example.auth.application.exception.AssumeTenantDeniedException}
 * (NOT a {@code false} return for failures, NOT a swallowed default). The caller
 * (the assume-tenant provider) treats the exception as a hard deny → no token,
 * {@code invalid_grant}. This is deliberately the opposite of the account-service
 * {@code entitled_domains} fail-soft policy.
 */
public interface OperatorAssignmentPort {

    /**
     * TASK-BE-338 (ADR-MONO-020 D3 amendment) — confirms the assignment AND
     * resolves the selected assignment's per-assignment data-scope
     * ({@code org_scope}).
     *
     * @param oidcSubject the operator's GAP OIDC {@code sub} (account_id) from the
     *                    validated subject token
     * @param tenantId    the selected (assume-target) customer tenant id
     * @return the confirmed assignment + its {@code org_scope} (subtree-root ids;
     *         {@code null} when unset → the caller defaults to {@code ["*"]})
     * @throws com.example.auth.application.exception.AssumeTenantDeniedException
     *         on a negative answer OR any admin-service failure (fail-closed)
     */
    AssignmentResult resolveAssignment(String oidcSubject, String tenantId);

    /**
     * Boolean convenience kept for callers that need only the verdict (the
     * resolved org_scope is ignored). Same fail-closed contract.
     *
     * @return {@code true} iff admin-service confirms the assignment
     * @throws com.example.auth.application.exception.AssumeTenantDeniedException
     *         on a negative answer OR any admin-service failure (fail-closed)
     */
    default boolean isAssigned(String oidcSubject, String tenantId) {
        return resolveAssignment(oidcSubject, tenantId).assigned();
    }

    /**
     * TASK-BE-338 — the confirmed assignment + its per-assignment data-scope.
     *
     * @param assigned always {@code true} when returned (a non-assignment is a
     *                 thrown {@link com.example.auth.application.exception.AssumeTenantDeniedException},
     *                 never a {@code false} result)
     * @param orgScope the selected assignment's {@code org_scope} (department
     *                 subtree-root ids), or {@code null} when unset / absent —
     *                 the customizer maps {@code null}/empty/absent → {@code ["*"]}
     *                 (net-zero). An explicit non-empty list is carried verbatim.
     */
    record AssignmentResult(boolean assigned, List<String> orgScope) {}
}
