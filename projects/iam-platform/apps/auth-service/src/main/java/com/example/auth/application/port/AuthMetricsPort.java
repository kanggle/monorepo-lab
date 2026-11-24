package com.example.auth.application.port;

/**
 * TASK-BE-664 — the two security counters auth-service observability.md § 필수 메트릭 requires and
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

    /** One login attempt reached {@code outcome}. Call exactly once per attempt. */
    void loginAttempt(LoginOutcome outcome);

    /** One refresh-token reuse was detected. Call exactly once per detection. */
    void tokenReuseDetected();
}
