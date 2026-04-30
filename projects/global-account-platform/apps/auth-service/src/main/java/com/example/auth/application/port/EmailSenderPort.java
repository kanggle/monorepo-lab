package com.example.auth.application.port;

import com.example.auth.application.exception.EmailSendException;

/**
 * Port interface for outbound transactional email.
 *
 * <p>Currently scoped to password-reset notifications only — additional flows
 * (signup verification, suspicious-login alerts, etc.) should add new methods
 * here when they land. The single-method shape keeps the contract narrow so
 * the production SMTP/SES adapter can be substituted without re-exposing
 * unrelated email surface area to the application layer.</p>
 *
 * <p>Implementations live in {@code infrastructure/email/}. During development
 * the {@code Slf4jEmailSender} stub is registered automatically; a real
 * implementation should be marked {@code @Component} and named
 * {@code "realEmailSender"} so the stub backs off (see TASK-BE-108).</p>
 */
public interface EmailSenderPort {

    /**
     * Send a password reset email. The implementation is expected to render
     * a link from {@code resetToken} (e.g. {@code /password-reset?token=...})
     * before sending.
     *
     * <p>Callers treat send failures as best-effort and catch
     * {@link EmailSendException} — the controller surfaces a uniform 204
     * regardless of whether the address exists or the send succeeds.</p>
     *
     * <p><strong>Adapter contract:</strong> production implementations must
     * wrap all send-path failures in {@link EmailSendException} so the
     * caller's {@code catch (EmailSendException)} guard fires correctly.</p>
     *
     * @throws EmailSendException if the underlying transport fails
     */
    void sendPasswordResetEmail(String toEmail, String resetToken);
}
