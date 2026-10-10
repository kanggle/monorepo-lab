package com.example.account.application.service;

import com.example.account.application.command.SendOperatorInvitationMailCommand;
import com.example.account.application.exception.EmailDeliveryException;
import com.example.account.application.exception.InvitationEmailSendFailedException;
import com.example.account.application.port.OperatorInvitationNotifier;
import com.example.account.domain.repository.TenantRepository;
import com.example.account.domain.tenant.Tenant;
import com.example.account.domain.tenant.TenantId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * TASK-MONO-772 S2 (ADR-MONO-080 D6 · owner decision OD-4) — {@code POST /internal/notifications/operator-invitation}
 * (admin-to-account.md). admin-service has no mail adapter; it hands the committed invitation's raw token to this
 * service, which sends it with the {@code TASK-MONO-770} machinery and answers whether the mail left.
 *
 * <p><b>Stores nothing.</b> No token store, no event, no audit row (the audit authority is admin-service's
 * {@code admin_actions}). The token reaches the notifier's link and nothing else — R4 and rider R4 («토큰 원문
 * 미저장») bind this service too.
 *
 * <p>The company name in the body is this service's own {@code tenants.display_name}; a missing tenant or any
 * read failure falls back to the id (a mail with the slug is better than no mail). A send failure is an answer,
 * not a swallowed WARN: transient → 503, permanent → 422; a notifier that throws something unclassified is
 * transient (calling an unknown failure permanent makes the operator give up on something that may work).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SendOperatorInvitationMailUseCase {

    private final OperatorInvitationNotifier notifier;
    private final TenantRepository tenantRepository;

    public void send(SendOperatorInvitationMailCommand cmd) {
        String company = companyName(cmd.tenantId());
        try {
            notifier.sendOperatorInvitation(cmd.to(), cmd.token(), company, cmd.inviterDisplayName(), cmd.expiresAt());
        } catch (EmailDeliveryException e) {
            log.warn("Operator invitation email not sent (tenant={}): kind={}", cmd.tenantId(), e.getKind());
            throw new InvitationEmailSendFailedException(e.getKind());
        } catch (RuntimeException e) {
            log.warn("Operator invitation email not sent (tenant={}): unclassified failure type={}",
                    cmd.tenantId(), e.getClass().getName());
            throw new InvitationEmailSendFailedException(EmailDeliveryException.Kind.TRANSIENT);
        }
    }

    String companyName(String tenantId) {
        try {
            return tenantRepository.findById(new TenantId(tenantId))
                    .map(Tenant::getDisplayName)
                    .filter(name -> name != null && !name.isBlank())
                    .orElse(tenantId);
        } catch (RuntimeException e) {
            return tenantId;
        }
    }
}
