package com.example.account.application.port;

/**
 * TASK-BE-668 — {@code account_signup_total{result}} (account-service observability.md § 필수 메트릭 :13).
 *
 * <p>A port so the application layer ({@code SignupUseCase}) and the presentation layer (the
 * bean-validation arm of {@code GlobalExceptionHandler}) do not import Micrometer; the implementation
 * is {@link com.example.account.infrastructure.metrics.MicrometerSignupMetrics}.
 *
 * <p><b>What is a signup attempt.</b> A request to the public {@code POST /api/accounts/signup} —
 * the flow the {@code AccountSignupFailureHigh} alert (duplicate + validation_error ratio, bot signups)
 * is about. The server-to-server account creations (social first login, the operator-invitation pool
 * signup) are not counted.
 *
 * <p><b>Counting rule.</b> Once per attempt, at the point its outcome is decided. An infrastructure
 * failure (auth-service down, a DB error) is none of the three results and is not counted — counting
 * it as {@code validation_error} would let an outage page the bot-signup alert.
 */
public interface SignupMetricsPort {

    /** {@code result} tag values — exactly the three the spec lists. */
    enum SignupOutcome {
        /** The account was created. */
        SUCCESS("success"),
        /** Refused with the duplicate answer ({@code ACCOUNT_ALREADY_EXISTS}). */
        DUPLICATE("duplicate"),
        /** Refused with {@code VALIDATION_ERROR} (request validation, password policy, e-mail format). */
        VALIDATION_ERROR("validation_error");

        private final String tagValue;

        SignupOutcome(String tagValue) {
            this.tagValue = tagValue;
        }

        public String tagValue() {
            return tagValue;
        }
    }

    /** One signup attempt reached {@code outcome}. Call exactly once per attempt. */
    void signupAttempt(SignupOutcome outcome);
}
