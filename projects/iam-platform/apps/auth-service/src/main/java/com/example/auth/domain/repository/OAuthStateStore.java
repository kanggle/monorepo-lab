package com.example.auth.domain.repository;

import com.example.auth.domain.oauth.OAuthProvider;

import java.util.Optional;

/**
 * Port for the OAuth authorization-flow {@code state} CSRF token store.
 *
 * <p>The {@code authorize} endpoint persists a freshly generated state with
 * its bound provider; the {@code callback} endpoint atomically consumes it
 * (single-use, get-and-delete). TTL is enforced by the implementation per
 * {@code specs/features/oauth-social-login.md} (currently 10 minutes,
 * TASK-BE-087).
 *
 * <p>Fail-closed: a backing-store outage during {@link #consumeAtomic} MUST
 * surface as either {@link Optional#empty()} or a propagated exception — never
 * as a successful match — so that an unverifiable state cannot be treated as
 * valid by the caller.
 */
public interface OAuthStateStore {

    /**
     * Persists {@code state → provider} for one TTL window. The key space is
     * disjoint from any other auth-service Redis usage.
     */
    void store(String state, OAuthProvider provider);

    /**
     * Atomically reads and deletes the state entry. Returns the provider it
     * was originally bound to, or {@link Optional#empty()} if the state is
     * unknown / expired / already consumed.
     */
    Optional<OAuthProvider> consumeAtomic(String state);
}
