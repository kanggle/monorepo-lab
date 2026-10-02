package com.example.auth.application.port;

/**
 * TASK-BE-618 (auth-to-admin.md § GET /internal/operators/facet) — asks admin-service whether a consumer
 * account carries an operator facet, before its credential is moved into the consumer pool.
 *
 * <p><b>fail-CLOSED</b>: returns only on a definite answer from admin-service. Any failure — 4xx, 5xx,
 * timeout, circuit-open, IO, malformed body — throws
 * {@link com.example.auth.application.exception.OperatorFacetUnavailableException}, and the caller moves
 * nothing. Moving without the answer could drag an operator's account into the pool.
 */
public interface OperatorFacetPort {

    /**
     * @param accountId  the account about to be moved
     * @param identityId the credential's central identity, or {@code null} when it has none
     * @return {@code true} when any operator row has {@code oidc_subject = accountId} or
     *         {@code identity_id = identityId}
     * @throws com.example.auth.application.exception.OperatorFacetUnavailableException when
     *         admin-service cannot answer
     */
    boolean isOperatorFaceted(String accountId, String identityId);
}
