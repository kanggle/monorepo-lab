package com.example.auth.infrastructure.email;

import com.example.auth.application.port.EmailSenderPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Logging-only {@link EmailSenderPort} for non-production profiles
 * (TASK-BE-242).
 *
 * <p>This adapter exists so the password-reset flow can be exercised end-to-end
 * in dev / e2e / integration environments without a real SMTP/SES connection.
 * It logs only that a password reset email was queued, with the recipient
 * address masked and the reset token redacted. The reset token is never logged
 * in plain text.</p>
 *
 * <h3>Why {@code @Profile("!prod")} instead of {@code @ConditionalOnMissingBean}</h3>
 *
 * <p>An earlier stub ({@code Slf4jEmailSender}, still present) used
 * {@code @ConditionalOnMissingBean(EmailSenderPort.class)} on a
 * {@code @Component} class. That guard is unreliable when applied to a
 * component-scanned class because the condition is evaluated during component
 * scanning, where ordering vs. other component-scanned beans is not
 * guaranteed. In the {@code e2e} profile this manifested as the application
 * failing to start with:
 *
 * <pre>
 * Parameter 3 of constructor in
 *   com.example.auth.application.RequestPasswordResetUseCase
 *   required a bean of type
 *   'com.example.auth.application.port.EmailSenderPort'
 *   that could not be found.
 * </pre>
 *
 * <p>{@code @Profile("!prod")} is evaluated deterministically against the
 * active environment, so the stub is guaranteed to register in any non-prod
 * profile (including {@code e2e}, {@code dev}, {@code test}) and is
 * guaranteed to <strong>not</strong> register in {@code prod}. If a real SMTP
 * adapter is added later, it should be {@code @Profile("prod")} (or
 * unconditional and the stub left as-is) — the two profile predicates are
 * disjoint, so they cannot both be active and the application context will
 * always wire exactly one sender per environment.</p>
 *
 * <h3>Failure-safety in prod</h3>
 *
 * <p>Because this stub is excluded from {@code prod}, an accidental prod
 * deployment without a real SMTP adapter will <strong>fail-fast</strong>
 * during context initialisation rather than silently swallow password reset
 * emails (matches the failure-scenario guard in TASK-BE-242).</p>
 *
 * <h3>R4 (regulated PII) compliance</h3>
 *
 * <p>Per {@code rules/traits/regulated.md} R4: tokens and other single-use
 * credentials must never be logged at any level, and PII (such as recipient
 * email addresses) must be masked. The token argument is intentionally
 * redacted in the log line — only the masked recipient and redacted subject
 * are emitted.</p>
 */
@Slf4j
@Component
@Profile("!prod")
public class LoggingEmailSender implements EmailSenderPort {

    @Override
    public void sendPasswordResetEmail(String toEmail, String resetToken) {
        // R4: resetToken MUST NOT appear in the log line. Only the masked
        // recipient address is emitted. Subject is logged without the token.
        log.info(
                "[DEV STUB] Password reset email queued — to={}, subject={}",
                maskedEmail(toEmail),
                "Password Reset Request"
        );
    }

    /**
     * Masks an email address for safe logging.
     *
     * <p>Keeps the first character of the local part, replaces the rest with
     * {@code ***}, and preserves the domain. Returns {@code "[masked]"} for
     * any input that is null, missing an {@code @}, or causes an exception.</p>
     *
     * <p>Mirrors the masking helper in {@code account-service}'s
     * {@code LoggingEmailVerificationNotifier} (TASK-BE-236) so the dev-stub
     * log format is consistent across services.</p>
     */
    private String maskedEmail(String email) {
        try {
            if (email == null) {
                return "[masked]";
            }
            int atIndex = email.indexOf('@');
            if (atIndex < 0) {
                return "[masked]";
            }
            String local = email.substring(0, atIndex);
            String domain = email.substring(atIndex);
            if (local.isEmpty()) {
                return "[masked]";
            }
            return local.charAt(0) + "***" + domain;
        } catch (RuntimeException e) {
            return "[masked]";
        }
    }
}
