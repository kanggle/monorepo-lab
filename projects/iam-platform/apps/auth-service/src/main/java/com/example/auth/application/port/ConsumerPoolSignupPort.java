package com.example.auth.application.port;

/**
 * TASK-MONO-772 S3 (auth-to-account.md § {@code POST /internal/consumer-pool/signups}) — the site-less pool signup
 * behind the IdP page {@code /operator-invitations/signup}: a pool account only, no site membership, no
 * {@code account.created}. Not retried (a write); every answer is a value the page maps to a screen.
 */
public interface ConsumerPoolSignupPort {

    enum Outcome {
        CREATED,
        /**
         * {@code 409 ACCOUNT_ALREADY_EXISTS} — a pool account with this email, OR a consumer SITE account with it
         * (multi-tenancy.md § 소비자 계정 풀 § 2 coexistence: the same code on purpose, so the two cannot be told
         * apart from here).
         */
        ALREADY_EXISTS,
        /** {@code 400/422 VALIDATION_ERROR} — format or password policy. */
        INVALID,
        /** {@code 409 CONSUMER_POOL_DISABLED} — this deployment creates no pool accounts. */
        NOT_POSSIBLE,
        /** {@code 429} · 5xx · timeout · anything else. */
        UNAVAILABLE
    }

    Outcome signup(String email, String password, String displayName);
}
