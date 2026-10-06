package com.example.account.application.result;

/**
 * TASK-BE-618 (account-maintenance-internal.md § 건너뛰는 사유) — what happened to one candidate of the
 * consumer-pool legacy move. Everything except {@link #MOVED} is a skip: nothing was written for it.
 * A failure (rollback for any other reason) is not an outcome here — the orchestrator counts it as failed.
 */
public enum LegacyMoveOutcome {
    MOVED,
    /** A stored {@code SELLER} role on the site — moved in TASK-MONO-745's step. */
    SELLER,
    /** The same email has an account on another consumer site — left in place (linking, TASK-MONO-743, was closed unbuilt on 2026-10-05). */
    TWO_SITE,
    /** The same email already has a pool account — § 2 coexistence; not papered over by moving. */
    POOL_EMAIL_EXISTS,
    /** Moving the identity row would collide with a pool identity, or the identity is shared. */
    IDENTITY_CONFLICT,
    /** auth-service (asking admin-service) found an operator facet — TASK-MONO-746. */
    OPERATOR_FACETED,
    /** The account has social identities — handed to TASK-BE-617. */
    SOCIAL_LINKED,
    /** A pool credential with the same email exists. */
    POOL_CREDENTIAL_EXISTS,
    /** The account's credential lives in neither the pool nor its site. */
    CREDENTIAL_TENANT_MISMATCH,
    /** Re-read under the lock, the account is no longer a site account (moved concurrently, deleted). */
    NO_LONGER_CANDIDATE;

    public boolean isSkip() {
        return this != MOVED;
    }
}
