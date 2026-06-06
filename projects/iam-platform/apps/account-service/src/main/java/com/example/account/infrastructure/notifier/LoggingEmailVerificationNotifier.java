package com.example.account.infrastructure.notifier;

import com.example.account.application.port.EmailVerificationNotifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Logging-only {@link EmailVerificationNotifier} for non-production profiles
 * (TASK-BE-236).
 *
 * <p>This adapter exists so the email verification flow can be exercised in
 * dev / e2e / integration environments without a real SMTP/SES connection.
 * It logs only that a verification email was queued, with the recipient
 * address masked. The verification token is never logged.</p>
 *
 * <h3>Why {@code @Profile("!prod")} instead of {@code @ConditionalOnMissingBean}</h3>
 *
 * <p>An earlier stub ({@code Slf4jEmailVerificationNotifier}, removed in
 * TASK-BE-236) used {@code @ConditionalOnMissingBean(EmailVerificationNotifier.class)}
 * on a {@code @Component} class. That guard is unreliable when applied to a
 * component-scanned class because the condition is evaluated during component
 * scanning, where ordering vs. other component-scanned beans is not
 * guaranteed. In the {@code e2e} profile this manifested as the application
 * failing to start with:
 *
 * <pre>
 * Parameter 2 of constructor in
 *   com.example.account.application.service.SendVerificationEmailUseCase
 *   required a bean of type
 *   'com.example.account.application.port.EmailVerificationNotifier'
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
 * always wire exactly one notifier per environment.</p>
 *
 * <h3>Failure-safety in prod</h3>
 *
 * <p>Because this stub is excluded from {@code prod}, an accidental prod
 * deployment without a real SMTP adapter will <strong>fail-fast</strong>
 * during context initialisation rather than silently swallow verification
 * emails (matches the failure-scenario guard in TASK-BE-236).</p>
 *
 * <h3>R4 (regulated PII) compliance</h3>
 *
 * <p>Per {@code rules/traits/regulated.md} R4: tokens and other single-use
 * credentials must never be logged at any level, and PII (such as recipient
 * email addresses) must be masked. The token argument is intentionally
 * dropped on the floor — only the masked recipient is emitted.</p>
 */
@Slf4j
@Component
@Profile("!prod")
public class LoggingEmailVerificationNotifier implements EmailVerificationNotifier {

    @Override
    public void sendVerificationEmail(String toEmail, String token) {
        // R4: token MUST NOT appear in the log line. Only the masked
        // recipient address is emitted.
        log.info("[DEV STUB] Email verification queued — to={}", maskedEmail(toEmail));
    }

    /**
     * Masks an email address for safe logging.
     *
     * <p>Keeps the first character of the local part, replaces the rest with
     * {@code ***}, and preserves the domain. Returns {@code "[masked]"} for
     * any input that is null, missing an {@code @}, or causes an exception.</p>
     *
     * <p>Mirrors the masking helper in {@code auth-service}'s
     * {@code Slf4jEmailSender} (TASK-BE-111) so the dev-stub log format is
     * consistent across services.</p>
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
