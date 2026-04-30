package com.example.auth.infrastructure.email;

import com.example.auth.application.port.EmailSenderPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Development-only {@link EmailSenderPort} stub.
 *
 * <p>This adapter exists so the password-reset flow can be exercised end-to-end
 * without an SMTP/SES connection. It logs only that a password reset email was
 * queued, with the recipient address masked. The reset token is never logged.</p>
 *
 * <p><strong>Production note (TASK-BE-108 / TASK-BE-111):</strong> a real
 * implementation must be supplied before this service is deployed to any
 * non-development environment. The real bean should implement
 * {@link EmailSenderPort} so this stub's
 * {@link ConditionalOnMissingBean} guard backs off automatically. Do
 * <strong>not</strong> remove the {@code @ConditionalOnMissingBean} — it is the
 * mechanism that prevents this stub from quietly co-existing with a real
 * sender.</p>
 *
 * <p>Per {@code rules/traits/regulated.md} R4: tokens and secrets must never
 * be logged at any level, and PII (such as email addresses) must be masked.</p>
 */
@Slf4j
@Component
@ConditionalOnMissingBean(EmailSenderPort.class)
public class Slf4jEmailSender implements EmailSenderPort {

    @Override
    public void sendPasswordResetEmail(String toEmail, String resetToken) {
        log.info("[DEV STUB] Password reset email queued — to={}", maskedEmail(toEmail));
    }

    /**
     * Masks an email address for safe logging.
     *
     * <p>Keeps the first character of the local part, replaces the rest with
     * {@code ***}, and preserves the domain. Returns {@code "[masked]"} for any
     * input that is null, missing an {@code @}, or causes an exception.</p>
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
