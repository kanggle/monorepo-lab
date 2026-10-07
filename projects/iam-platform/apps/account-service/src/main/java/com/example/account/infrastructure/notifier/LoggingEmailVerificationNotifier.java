package com.example.account.infrastructure.notifier;

import com.example.account.application.port.EmailVerificationNotifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
 * guaranteed to <strong>not</strong> register in {@code prod}.</p>
 *
 * <h3>TASK-MONO-770 — and only while mail is off</h3>
 *
 * <p>The real adapter ({@link SmtpEmailVerificationNotifier}) is selected by the property
 * {@code iam.mail.enabled=true}, not by a profile (the demo runs IAM under {@code e2e}, where a
 * {@code @Profile("prod")} adapter would never run). This stub therefore carries the complementary condition
 * {@code iam.mail.enabled=false|absent} on top of {@code !prod}: the two conditions are disjoint, so exactly one
 * notifier is wired in every environment except {@code prod} with mail off — where none is, and the context
 * fails fast as before. Both conditions are property/profile predicates evaluated against the Environment, not
 * the bean-registry-order-dependent {@code @ConditionalOnMissingBean} this class was written to avoid.</p>
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
@ConditionalOnProperty(prefix = "iam.mail", name = "enabled", havingValue = "false", matchIfMissing = true)
public class LoggingEmailVerificationNotifier implements EmailVerificationNotifier {

    @Override
    public void sendVerificationEmail(String toEmail, String token) {
        // R4: token MUST NOT appear in the log line. Only the masked
        // recipient address is emitted.
        // Masking rule: RecipientMask (shared with the SMTP adapter since TASK-MONO-770).
        log.info("[DEV STUB] Email verification queued — to={}", RecipientMask.mask(toEmail));
    }
}
