package com.example.auth.application.port;

/**
 * TASK-BE-327 (ADR-MONO-020 § 3.3 step 2, D2) — port for the issuance-time
 * assume-tenant authorization check against admin-service. Implementation lives
 * in {@code infrastructure/client/AdminAssignmentClient}.
 *
 * <p><b>fail-CLOSED contract</b>: {@link #isAssigned(String, String)} returns
 * {@code true} ONLY when admin-service positively confirms the operator's D1
 * assignment to the selected tenant. ANY failure — not-assigned, admin-service
 * down, timeout, 4xx/5xx, circuit-open, IO — surfaces as
 * {@link com.example.auth.application.exception.AssumeTenantDeniedException}
 * (NOT {@code false} for failures, NOT a swallowed default). The caller (the
 * assume-tenant provider) treats the exception as a hard deny → no token,
 * {@code invalid_grant}. This is deliberately the opposite of the account-service
 * {@code entitled_domains} fail-soft policy.
 */
public interface OperatorAssignmentPort {

    /**
     * @param oidcSubject the operator's GAP OIDC {@code sub} (account_id) from the
     *                    validated subject token
     * @param tenantId    the selected (assume-target) customer tenant id
     * @return {@code true} iff admin-service confirms the assignment
     * @throws com.example.auth.application.exception.AssumeTenantDeniedException
     *         on a negative answer OR any admin-service failure (fail-closed)
     */
    boolean isAssigned(String oidcSubject, String tenantId);
}
