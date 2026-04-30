package com.example.account.application.event;

import com.example.account.application.util.DigestUtils;
import com.example.account.domain.account.Account;
import com.example.account.domain.event.AccountDomainEvent;
import com.example.messaging.event.BaseEventPublisher;
import com.example.messaging.outbox.OutboxWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class AccountEventPublisher extends BaseEventPublisher {

    public AccountEventPublisher(OutboxWriter outboxWriter, ObjectMapper objectMapper) {
        super(outboxWriter, objectMapper);
    }

    public void publishAccountCreated(Account account, String locale) {
        String emailHash = DigestUtils.sha256Short(account.getEmail(), 10);
        save(account.getId(), account.buildCreatedEvent(emailHash, locale));
    }

    public void publishStatusChanged(Account account, String previousStatus, String reasonCode,
                                      String actorType, String actorId, Instant occurredAt) {
        save(account.getId(), account.buildStatusChangedEvent(previousStatus, reasonCode, actorType, actorId, occurredAt));
    }

    public void publishAccountLocked(Account account, String reasonCode,
                                      String actorType, String actorId, Instant lockedAt) {
        save(account.getId(), account.buildLockedEvent(reasonCode, actorType, actorId, lockedAt));
    }

    public void publishAccountUnlocked(Account account, String reasonCode,
                                        String actorType, String actorId, Instant unlockedAt) {
        save(account.getId(), account.buildUnlockedEvent(reasonCode, actorType, actorId, unlockedAt));
    }

    public void publishAccountDeleted(Account account, String reasonCode,
                                       String actorType, String actorId,
                                       Instant deletedAt, Instant gracePeriodEndsAt) {
        save(account.getId(), account.buildDeletedEvent(reasonCode, actorType, actorId, deletedAt, gracePeriodEndsAt, false));
    }

    /**
     * TASK-BE-231: Published when the provisioning API replaces an account's role set.
     */
    public void publishRolesChanged(Account account, java.util.List<String> roles,
                                    String actorType, String actorId, java.time.Instant occurredAt) {
        save(account.getId(), account.buildRolesChangedEvent(roles, actorType, actorId, occurredAt));
    }

    public void publishAccountDeletedAnonymized(Account account, String reasonCode,
                                                 String actorType, String actorId,
                                                 Instant deletedAt, Instant gracePeriodEndsAt) {
        save(account.getId(), account.buildDeletedEvent(reasonCode, actorType, actorId, deletedAt, gracePeriodEndsAt, true));
    }

    private void save(String accountId, AccountDomainEvent event) {
        saveEvent("Account", accountId, event.eventType(), event.payload());
    }
}
