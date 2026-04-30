package com.example.account.infrastructure.email;

import com.example.account.application.port.EmailVerificationNotifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Development-only {@link EmailVerificationNotifier} stub (TASK-BE-114).
 *
 * <p>This adapter exists so the email verification flow can be exercised
 * end-to-end without an SMTP/SES connection. It logs only that a verification
 * email was queued, with the recipient address masked. The verification token
 * is never logged.</p>
 *
 * <p><strong>Production note:</strong> a real implementation must be supplied
 * before this service is deployed to any non-development environment. The real
 * bean should implement {@link EmailVerificationNotifier} so this stub's
 * {@link ConditionalOnMissingBean} guard backs off automatically. Do
 * <strong>not</strong> remove the {@code @ConditionalOnMissingBean} — it is the
 * mechanism that prevents this stub from quietly co-existing with a real
 * notifier (mirrors the BE-111 pattern in
 * {@code auth-service.infrastructure.email.Slf4jEmailSender}).</p>
 *
 * <p>Per {@code rules/traits/regulated.md} R4: tokens and secrets must never
 * be logged at any level, and PII (such as email addresses) must be masked.</p>
 */
@Slf4j
@Component
@ConditionalOnMissingBean(EmailVerificationNotifier.class)
public class Slf4jEmailVerificationNotifier implements EmailVerificationNotifier {

    @Override
    public void sendVerificationEmail(String toEmail, String token) {
        log.info("[DEV STUB] Email verification queued — to={}", maskedEmail(toEmail));
    }

    /**
     * Masks an email address for safe logging.
     *
     * <p>Keeps the first character of the local part, replaces the rest with
     * {@code ***}, and preserves the domain. Returns {@code "[masked]"} for any
     * input that is null, missing an {@code @}, or causes an exception.</p>
     *
     * <p>Mirrors {@code Slf4jEmailSender.maskedEmail} (TASK-BE-111) — keep the
     * two implementations in sync so log format stays consistent across
     * services.</p>
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
        } catch (Exception e) {
            return "[masked]";
        }
    }
}
