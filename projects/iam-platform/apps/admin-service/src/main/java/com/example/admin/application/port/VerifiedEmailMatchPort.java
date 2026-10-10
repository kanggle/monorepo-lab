package com.example.admin.application.port;

import java.time.Instant;

/**
 * TASK-MONO-772 S2 (AC-0 F3 · D-3 step 4) — «is this account a pool account that verified THIS email?», judged by
 * account-service ({@code POST /internal/accounts/{accountId}/verified-email:match}, admin-to-account.md). The
 * predicate is {@code TASK-MONO-770}'s {@code VerifiedEmailRequirement}, called where it lives — admin-service
 * keeps no copy of it.
 *
 * <p>The three definitive refusals come back as an {@link Outcome}; anything else (5xx · timeout · circuit open ·
 * an unknown 4xx · a 200 without {@code emailVerifiedAt}) is thrown as a
 * {@link com.example.admin.application.exception.DownstreamFailureException} — the caller must then refuse
 * (fail-closed: never attach without a verdict). The S3 acceptance is the consumer; S2 ships the adapter.
 */
public interface VerifiedEmailMatchPort {

    enum Outcome {
        /** ACTIVE pool account, same email, verified — {@link MatchResult#emailVerifiedAt()} is set. */
        MATCHED,
        /** account-service 404 — not an ACTIVE pool account (missing · a site/B2B account · not ACTIVE: one answer, S1-3). */
        NOT_ELIGIBLE,
        /** 403 {@code ACCOUNT_EMAIL_MISMATCH}. */
        EMAIL_MISMATCH,
        /** 403 {@code EMAIL_NOT_VERIFIED} — the 770 shared name, passed through unchanged by the acceptance. */
        NOT_VERIFIED
    }

    record MatchResult(Outcome outcome, Instant emailVerifiedAt) {}

    MatchResult match(String accountId, String expectedEmail);
}
