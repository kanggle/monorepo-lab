package com.example.auth.application.port;

/**
 * TASK-BE-618 (auth-to-admin.md § GET /internal/operators/facet) — asks admin-service whether a consumer
 * account carries an operator facet, before its credential is moved into the consumer pool.
 *
 * <p><b>TASK-MONO-772 S6 — the identity axis only</b> ({@code axes=IDENTITY}; auth-internal.md 판정 6): an
 * ADR-MONO-044 D5 self-onboarded operator ({@code admin_operators.oidc_subject} = this account) is no longer a
 * reason to skip — ADR-MONO-080 D6 moves it into the pool under the same id, so its operator row needs no write
 * and the same {@code sub} still reaches it. An operator identity link (ADR-MONO-034 U3) still is.
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
     * @return {@code true} when any operator row has {@code identity_id = identityId} (772 S6 — the
     *         {@code oidc_subject} axis is no longer asked)
     * @throws com.example.auth.application.exception.OperatorFacetUnavailableException when
     *         admin-service cannot answer
     */
    boolean isOperatorFaceted(String accountId, String identityId);
}
