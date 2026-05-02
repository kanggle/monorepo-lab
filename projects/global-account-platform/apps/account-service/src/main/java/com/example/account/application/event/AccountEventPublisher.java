package com.example.account.application.event;

import com.example.account.application.util.DigestUtils;
import com.example.account.domain.account.Account;
import com.example.account.domain.event.AccountDomainEvent;
import com.example.messaging.event.BaseEventPublisher;
import com.example.messaging.outbox.OutboxWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;

/**
 * TASK-BE-248: All publish* methods require an explicit {@code tenantId} argument.
 * A null or blank tenantId throws {@link IllegalArgumentException} immediately,
 * so any call-site that omits the tenant context fails fast at runtime (and,
 * thanks to the signature change, fails to compile if a caller does not supply
 * the argument at all).
 *
 * <p>The tenantId value is validated here at the publisher boundary; the actual
 * payload injection happens inside {@link Account#buildCreatedEvent} and sibling
 * methods, which read {@code account.getTenantId()} directly. The explicit
 * parameter serves as a compile-time contract — callers must consciously supply
 * the tenant context.
 */
@Component
public class AccountEventPublisher extends BaseEventPublisher {

    public AccountEventPublisher(OutboxWriter outboxWriter, ObjectMapper objectMapper) {
        super(outboxWriter, objectMapper);
    }

    public void publishAccountCreated(Account account, String tenantId, String locale) {
        requireTenantId(tenantId);
        String emailHash = DigestUtils.sha256Short(account.getEmail(), 10);
        save(account.getId(), account.buildCreatedEvent(emailHash, locale));
    }

    public void publishStatusChanged(Account account, String tenantId, String previousStatus,
                                     String reasonCode, String actorType, String actorId,
                                     Instant occurredAt) {
        requireTenantId(tenantId);
        save(account.getId(), account.buildStatusChangedEvent(previousStatus, reasonCode,
                actorType, actorId, occurredAt));
    }

    public void publishAccountLocked(Account account, String tenantId, String reasonCode,
                                     String actorType, String actorId, Instant lockedAt) {
        requireTenantId(tenantId);
        save(account.getId(), account.buildLockedEvent(reasonCode, actorType, actorId, lockedAt));
    }

    public void publishAccountUnlocked(Account account, String tenantId, String reasonCode,
                                       String actorType, String actorId, Instant unlockedAt) {
        requireTenantId(tenantId);
        save(account.getId(), account.buildUnlockedEvent(reasonCode, actorType, actorId, unlockedAt));
    }

    public void publishAccountDeleted(Account account, String tenantId, String reasonCode,
                                      String actorType, String actorId,
                                      Instant deletedAt, Instant gracePeriodEndsAt) {
        requireTenantId(tenantId);
        save(account.getId(), account.buildDeletedEvent(reasonCode, actorType, actorId,
                deletedAt, gracePeriodEndsAt, false));
    }

    /**
     * TASK-BE-231: Published when the provisioning API mutates an account's role set.
     *
     * <p>TASK-BE-255: Signature widened to carry both {@code beforeRoles} and
     * {@code afterRoles} plus the {@code changedBy} attribution string. The legacy
     * {@code roles} payload field is now an alias for {@code afterRoles} so
     * existing v2 consumers keep working unchanged. Add/remove use cases pass the
     * pre-mutation snapshot as {@code beforeRoles}; replace-all does the same.
     */
    public void publishRolesChanged(Account account, String tenantId,
                                    java.util.List<String> beforeRoles,
                                    java.util.List<String> afterRoles,
                                    String changedBy,
                                    String actorType, String actorId,
                                    java.time.Instant occurredAt) {
        requireTenantId(tenantId);
        save(account.getId(), account.buildRolesChangedEvent(
                beforeRoles, afterRoles, changedBy, actorType, actorId, occurredAt));
    }

    public void publishAccountDeletedAnonymized(Account account, String tenantId, String reasonCode,
                                                String actorType, String actorId,
                                                Instant deletedAt, Instant gracePeriodEndsAt) {
        requireTenantId(tenantId);
        save(account.getId(), account.buildDeletedEvent(reasonCode, actorType, actorId,
                deletedAt, gracePeriodEndsAt, true));
    }

    private void save(String accountId, AccountDomainEvent event) {
        saveEvent("Account", accountId, event.eventType(), event.payload());
    }

    /**
     * Guard: tenantId must be non-null and non-blank. Enforced at every publish
     * entry point so the constraint is caught as early as possible (TASK-BE-248).
     */
    private static void requireTenantId(String tenantId) {
        if (!StringUtils.hasText(tenantId)) {
            throw new IllegalArgumentException("tenantId required");
        }
    }
}
