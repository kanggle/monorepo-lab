package com.example.account.application.port;

import com.example.account.application.exception.EmailDeliveryException;

/**
 * Port interface for outbound email verification notifications (TASK-BE-114).
 *
 * <p>Sends the user-facing verification email containing a link with the
 * one-time token. The single-method shape keeps the contract narrow so a
 * production SMTP adapter can be substituted without re-exposing unrelated
 * email surface area to the application layer.</p>
 *
 * <h3>Which implementation is wired (TASK-MONO-770 — a property, not a profile)</h3>
 *
 * <table>
 *   <tr><th>{@code iam.mail.enabled}</th><th>profile</th><th>bean</th></tr>
 *   <tr><td>{@code true}</td><td>any</td><td>{@code SmtpEmailVerificationNotifier} (standard
 *       {@code spring.mail.*})</td></tr>
 *   <tr><td>{@code false}/absent</td><td>not {@code prod}</td><td>{@code LoggingEmailVerificationNotifier}</td></tr>
 *   <tr><td>{@code false}/absent</td><td>{@code prod}</td><td><b>none</b> — the context fails to start
 *       (TASK-BE-236's fail-fast guarantee: production never silently drops verification mail)</td></tr>
 * </table>
 *
 * <p>Why not {@code @Profile("prod")} for the real adapter, as the earlier Javadoc suggested: the demo runs IAM
 * with {@code SPRING_PROFILES_ACTIVE=e2e}, so a prod-only adapter would never run where the mail has to be read.
 *
 * <p>Per {@code rules/traits/regulated.md} R4: implementations must mask the
 * recipient address in any logs and must <strong>never</strong> log the token
 * (it is a single-use credential).</p>
 */
public interface EmailVerificationNotifier {

    /**
     * Send a verification email. The implementation renders a link from {@code token}
     * ({@code <iam.mail.verification-link-base-url>?token=...}) before sending.
     *
     * <p>TASK-MONO-770: a failure is reported, not swallowed — the caller tells the person whether to retry.
     *
     * @param toEmail recipient address (already normalised); must not be null
     * @param token   single-use verification token (UUID v4); must not be null
     * @throws EmailDeliveryException when the mail did not leave; its {@link EmailDeliveryException.Kind}
     *         says whether a retry can help. Its message must carry neither the address nor the token.
     */
    void sendVerificationEmail(String toEmail, String token);
}
