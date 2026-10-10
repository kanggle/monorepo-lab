package com.example.account.infrastructure.notifier;

import com.example.account.application.port.OperatorInvitationNotifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * TASK-MONO-772 S2 — logging-only {@link OperatorInvitationNotifier} while mail is off outside {@code prod}: the
 * exact conditions of {@link LoggingEmailVerificationNotifier} ({@code !prod} AND {@code iam.mail.enabled} false or
 * absent), so exactly one invitation notifier is wired wherever exactly one verification notifier is, and none in
 * {@code prod} with mail off (fail-fast). See that class for why these are property/profile predicates and not
 * {@code @ConditionalOnMissingBean}.
 *
 * <p>R4: the token argument is dropped on the floor — only the masked recipient is logged.
 */
@Slf4j
@Component
@Profile("!prod")
@ConditionalOnProperty(prefix = "iam.mail", name = "enabled", havingValue = "false", matchIfMissing = true)
public class LoggingOperatorInvitationNotifier implements OperatorInvitationNotifier {

    @Override
    public void sendOperatorInvitation(String toEmail, String token, String companyName, String inviterDisplayName,
                                       Instant expiresAt) {
        log.info("[DEV STUB] Operator invitation email queued — to={}", RecipientMask.mask(toEmail));
    }
}
