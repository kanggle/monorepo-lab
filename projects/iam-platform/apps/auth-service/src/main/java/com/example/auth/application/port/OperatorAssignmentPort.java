package com.example.auth.application.port;

import java.util.List;

/**
 * TASK-BE-327 (ADR-MONO-020 § 3.3 step 2, D2) — port for the issuance-time
 * assume-tenant authorization check against admin-service. Implementation lives
 * in {@code infrastructure/client/AdminAssignmentClient}.
 *
 * <p><b>fail-CLOSED contract</b>: {@link #resolveAssignment(String, String)}
 * returns ONLY when admin-service positively confirms the operator's D1
 * assignment to the selected tenant. ANY failure (not-assigned, admin-service
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
     * <p>TASK-MONO-299 (ADR-MONO-040 Phase 3 part B): account_id-only resolution —
     * the admin-service lookup keys on the account_id {@code oidcSubject} (the SAS
     * access-token {@code sub}). The Phase-2 transitional {@code subjectEmail}
     * legacy-fallback param is removed (the part-A backfill migrated
     * {@code admin_operators.oidc_subject} to account_id).
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
     * <p>TASK-BE-478 (ADR-MONO-045 §3.4 step 2b) — additionally carries the
     * additive {@link DelegatedScope} cross-org confinement block when (and ONLY
     * when) the assignment is <b>partnership-derived host reach</b>. {@code null}
     * for every normal assignment (byte-identical to the BE-338 two-field shape).
     *
     * @param assigned       always {@code true} when returned (a non-assignment is a
     *                       thrown {@link com.example.auth.application.exception.AssumeTenantDeniedException},
     *                       never a {@code false} result)
     * @param orgScope       the selected assignment's {@code org_scope} (department
     *                       subtree-root ids), or {@code null} when unset / absent —
     *                       the customizer maps {@code null}/empty/absent → {@code ["*"]}
     *                       (net-zero). An explicit non-empty list is carried verbatim.
     * @param delegatedScope the cross-org partnership cap ({@code delegated ∩
     *                       participant ∩ host-holds}, computed by admin-service), or
     *                       {@code null} for a normal (non-partnership) assignment. When
     *                       non-null the customizer caps the token's
     *                       {@code entitled_domains} to {@code host ∩ domains} and sets
     *                       {@code roles} to the delegated {@code roles} verbatim (never
     *                       re-deriving from the entitled domains).
     */
    record AssignmentResult(boolean assigned, List<String> orgScope, DelegatedScope delegatedScope) {

        /**
         * Back-compat convenience for a normal (non-partnership) assignment — a
         * two-field result with no cross-org cap.
         */
        public AssignmentResult(boolean assigned, List<String> orgScope) {
            this(assigned, orgScope, null);
        }
    }

    /**
     * TASK-BE-478 (ADR-MONO-045 §3.4 step 2b) — the additive cross-org confinement
     * block from the admin-service assignment-check ({@code delegatedScope} JSON): the
     * capped {@code {domains, roles}} a partnership-derived host tenant grants the
     * operator ({@code delegated_scope ∩ participant_scope ∩ host-holds}, computed by
     * admin-service). Present ONLY for partnership-derived host reach. The auth-service
     * customizer intersects the assume-tenant token's {@code entitled_domains} with
     * {@code domains} and sets {@code roles} to {@code roles} verbatim — admin scope is
     * never widened (the cross-org actor holds no {@code admin_operator_roles} in the
     * host, and {@code roles} is admin-role-free by the invite-time cap).
     *
     * @param domains the delegated (capped) domain keys; may be empty (→ the token
     *                carries no entitled domain → the domain gateway 403s)
     * @param roles   the delegated (capped) operator-role names; admin-role-free by
     *                construction (invite-time {@code ScopeSet.containsAdminRole} → 422)
     */
    record DelegatedScope(List<String> domains, List<String> roles) {}
}
