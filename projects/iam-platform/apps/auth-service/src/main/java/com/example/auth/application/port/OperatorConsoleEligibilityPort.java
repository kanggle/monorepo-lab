package com.example.auth.application.port;

/**
 * TASK-MONO-772 S4 (ADR-MONO-080 D6; auth-to-admin.md § GET /internal/operators/console-eligibility) — asks
 * admin-service whether a consumer-POOL account has a <b>live operator facet</b>, i.e. whether it may receive a
 * {@code platform-console-web} token.
 *
 * <p>The predicate is the one the operator token exchange applies ({@code admin_operators.oidc_subject =
 * accountId ∧ status = ACTIVE}) — NOT the status-blind two-axis facet read ({@link OperatorFacetPort}), which
 * answers a different question («may this account be moved»).
 *
 * <p><b>fail-CLOSED</b>: returns only on a definite answer. Any failure — 4xx, 5xx, timeout, circuit-open, IO,
 * a 200 without a boolean {@code eligible} — throws
 * {@link com.example.auth.application.exception.OperatorEligibilityUnavailableException}, and the issuer mints
 * no console token ({@code invalid_grant}, {@code error_description=operator_eligibility_unavailable}).
 */
public interface OperatorConsoleEligibilityPort {

    /**
     * @param accountId the pool principal's account id (= the console token's {@code sub})
     * @return {@code true} when an ACTIVE operator row has {@code oidc_subject = accountId}
     * @throws com.example.auth.application.exception.OperatorEligibilityUnavailableException when admin-service
     *         cannot answer
     */
    boolean isConsoleEligible(String accountId);
}
