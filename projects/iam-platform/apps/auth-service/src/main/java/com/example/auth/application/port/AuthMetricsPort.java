package com.example.auth.application.port;

/**
 * TASK-BE-664 (+ TASK-BE-668: {@code auth_token_issued_total{type}} and
 * {@code auth_token_refresh_total{result}}) — the two security counters auth-service observability.md § 필수 메트릭 requires and
 * that nothing produced before: {@code auth_login_total{result}} and
 * {@code auth_token_reuse_detected_total}.
 *
 * <p>A port so that the application-layer callers ({@code LoginUseCase},
 * {@code OAuthLoginUseCase}, {@code RefreshTokenUseCase}) do not import Micrometer — the
 * implementation is {@link com.example.auth.infrastructure.metrics.MicrometerAuthMetrics}. The
 * infrastructure callers ({@code CredentialAuthenticationProvider},
 * {@code SasRefreshTokenAuthenticationProvider}) use the same port, so the metric names and tag
 * values have exactly one home.
 *
 * <p><b>Counting rule.</b> A login attempt is counted once, at the point its outcome is decided; a
 * reuse is counted once, at the detection branch — never in an event listener and never after the
 * event write, so a failed {@code auth.token.reuse.detected} publish does not lose the detection.
 */
public interface AuthMetricsPort {

    /** {@code auth_login_total} {@code result} tag values — exactly the four the spec lists. */
    enum LoginOutcome {
        /** Credentials accepted — the session/token is issued. */
        SUCCESS("success"),
        /** Any other authentication refusal (wrong password, unknown email, non-ACTIVE status other than LOCKED …). */
        FAILURE("failure"),
        /** Refused by the login rate limit (failure counter over its threshold). */
        RATE_LIMITED("rate_limited"),
        /** Refused because the account is LOCKED. */
        ACCOUNT_LOCKED("account_locked");

        private final String tagValue;

        LoginOutcome(String tagValue) {
            this.tagValue = tagValue;
        }

        public String tagValue() {
            return tagValue;
        }
    }

    /**
     * TASK-BE-668 — {@code auth_token_issued_total} {@code type} tag values (observability.md:15).
     * An ID token is neither and is not counted.
     */
    enum IssuedTokenType {
        ACCESS("access"),
        REFRESH("refresh");

        private final String tagValue;

        IssuedTokenType(String tagValue) {
            this.tagValue = tagValue;
        }

        public String tagValue() {
            return tagValue;
        }
    }

    /**
     * TASK-BE-668 — {@code auth_token_refresh_total} {@code result} tag values (observability.md:16),
     * exactly the four the spec lists. A refresh refused for a reason outside these four (cross-tenant
     * mismatch, a client that does not own the token, a replay inside the grace window, an unknown
     * token on the SAS endpoint) is not counted.
     */
    enum RefreshOutcome {
        /** The token was rotated — a new pair was handed out. */
        SUCCESS("success"),
        /** Refused as expired (legacy endpoint: everything it answers {@code TOKEN_EXPIRED}). */
        EXPIRED("expired"),
        /** Refused because the token was already rotated — the reuse-detection branch. */
        REUSE_DETECTED("reuse_detected"),
        /** Refused because the token or its session was revoked. */
        REVOKED("revoked");

        private final String tagValue;

        RefreshOutcome(String tagValue) {
            this.tagValue = tagValue;
        }

        public String tagValue() {
            return tagValue;
        }
    }

    /** One login attempt reached {@code outcome}. Call exactly once per attempt. */
    void loginAttempt(LoginOutcome outcome);

    /** One refresh-token reuse was detected. Call exactly once per detection. */
    void tokenReuseDetected();

    /** TASK-BE-668 — one token of {@code type} was minted. Call once per minted token. */
    void tokenIssued(IssuedTokenType type);

    /** TASK-BE-668 — one refresh attempt reached {@code outcome}. Call exactly once per attempt. */
    void refreshAttempt(RefreshOutcome outcome);
}
