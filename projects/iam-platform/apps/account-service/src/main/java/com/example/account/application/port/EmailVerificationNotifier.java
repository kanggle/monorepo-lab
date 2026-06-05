package com.example.account.application.port;

/**
 * Port interface for outbound email verification notifications (TASK-BE-114).
 *
 * <p>Sends the user-facing verification email containing a link with the
 * one-time token. The single-method shape keeps the contract narrow so a
 * production SMTP/SES adapter can be substituted without re-exposing
 * unrelated email surface area to the application layer.</p>
 *
 * <p>Implementations live in {@code infrastructure/notifier/}. In non-prod
 * profiles (dev / e2e / test) the {@code LoggingEmailVerificationNotifier}
 * stub is registered via {@code @Profile("!prod")} (TASK-BE-236). A real
 * implementation should be registered as a {@code @Component} with
 * {@code @Profile("prod")} (or unconditional + the stub kept as-is) so the
 * two profiles are disjoint and the context wires exactly one notifier per
 * environment. An accidental prod deployment without a real adapter will
 * fail-fast at context init rather than silently dropping emails.</p>
 *
 * <p>Per {@code rules/traits/regulated.md} R4: implementations must mask the
 * recipient address in any logs and must <strong>never</strong> log the token
 * (it is a single-use credential).</p>
 */
public interface EmailVerificationNotifier {

    /**
     * Send a verification email. The implementation is expected to render a
     * link from {@code token} (e.g. {@code /signup/verify-email?token=...})
     * before sending.
     *
     * <p>Callers must treat send failures as best-effort: the use case should
     * catch and log exceptions rather than aborting the request, since the
     * token is already persisted in Redis and the user can retry.</p>
     *
     * @param toEmail recipient address (already normalised); must not be null
     * @param token   single-use verification token (UUID v4); must not be null
     */
    void sendVerificationEmail(String toEmail, String token);
}
