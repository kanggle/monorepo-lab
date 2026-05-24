package com.example.account.application.event;

import com.example.account.domain.account.Account;
import com.example.account.domain.event.AccountDomainEvent;
import com.example.common.id.UuidV7;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds {@link AccountDomainEvent} payloads at the application/outbox-shape
 * boundary. Moved out of the {@link Account} aggregate so the domain does not
 * need to know the outbox payload shape (event field names, UUID v7 eventId
 * stamping, optional-actor handling).
 *
 * <p>The aggregate exposes domain state via getters; this factory composes the
 * {@code Map<String, Object>} payload. {@link AccountEventPublisher} owns the
 * outbox write — this class is pure transformation and is safe to instantiate
 * directly in unit tests.
 */
@Component
public class AccountEventFactory {

    public AccountDomainEvent createdEvent(Account account, String emailHash, String locale) {
        return new AccountDomainEvent("account.created", Map.of(
                "accountId", account.getId(),
                "tenantId", account.getTenantId().value(),
                "emailHash", emailHash,
                "status", account.getStatus().name(),
                "locale", locale,
                "createdAt", account.getCreatedAt().toString()
        ));
    }

    public AccountDomainEvent statusChangedEvent(Account account, String previousStatus,
                                                  String reasonCode, String actorType,
                                                  String actorId, Instant occurredAt) {
        Map<String, Object> payload = new HashMap<>(Map.of(
                "accountId", account.getId(),
                "tenantId", account.getTenantId().value(),
                "previousStatus", previousStatus,
                "currentStatus", account.getStatus().name(),
                "reasonCode", reasonCode,
                "actorType", actorType,
                "occurredAt", occurredAt.toString()
        ));
        if (actorId != null) {
            payload.put("actorId", actorId);
        }
        return new AccountDomainEvent("account.status.changed", payload);
    }

    /**
     * TASK-BE-118: {@code eventId} must be UUID v7 per
     * specs/contracts/events/account-events.md so security-service can use it as
     * a time-ordered idempotency key ({@code account_lock_history.event_id}).
     */
    public AccountDomainEvent lockedEvent(Account account, String reasonCode, String actorType,
                                           String actorId, Instant lockedAt) {
        Map<String, Object> payload = new HashMap<>(Map.of(
                "eventId", UuidV7.randomString(),
                "accountId", account.getId(),
                "tenantId", account.getTenantId().value(),
                "reasonCode", reasonCode,
                "actorType", actorType,
                "lockedAt", lockedAt.toString()
        ));
        if (actorId != null) {
            payload.put("actorId", actorId);
        }
        return new AccountDomainEvent("account.locked", payload);
    }

    public AccountDomainEvent unlockedEvent(Account account, String reasonCode, String actorType,
                                             String actorId, Instant unlockedAt) {
        Map<String, Object> payload = new HashMap<>(Map.of(
                "accountId", account.getId(),
                "tenantId", account.getTenantId().value(),
                "reasonCode", reasonCode,
                "actorType", actorType,
                "unlockedAt", unlockedAt.toString()
        ));
        if (actorId != null) {
            payload.put("actorId", actorId);
        }
        return new AccountDomainEvent("account.unlocked", payload);
    }

    /**
     * TASK-BE-231: Event emitted when the provisioning API mutates an account's
     * role set. TASK-BE-255: schema v3 — adds {@code beforeRoles},
     * {@code afterRoles}, and {@code changedBy}. The legacy {@code roles}
     * field is preserved as an alias for {@code afterRoles} (forward-compat).
     */
    public AccountDomainEvent rolesChangedEvent(Account account, List<String> beforeRoles,
                                                 List<String> afterRoles, String changedBy,
                                                 String actorType, String actorId,
                                                 Instant occurredAt) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("accountId", account.getId());
        payload.put("tenantId", account.getTenantId().value());
        payload.put("roles", afterRoles);            // legacy alias (v2 consumers)
        payload.put("beforeRoles", beforeRoles);     // TASK-BE-255 (v3)
        payload.put("afterRoles", afterRoles);       // TASK-BE-255 (v3)
        payload.put("actorType", actorType);
        payload.put("occurredAt", occurredAt.toString());
        if (actorId != null) {
            payload.put("actorId", actorId);
        }
        if (changedBy != null) {
            payload.put("changedBy", changedBy);     // TASK-BE-255 (v3)
        }
        return new AccountDomainEvent("account.roles.changed", payload);
    }

    public AccountDomainEvent deletedEvent(Account account, String reasonCode, String actorType,
                                            String actorId, Instant deletedAt,
                                            Instant gracePeriodEndsAt, boolean anonymized) {
        Map<String, Object> payload = new HashMap<>(Map.of(
                "accountId", account.getId(),
                "tenantId", account.getTenantId().value(),
                "reasonCode", reasonCode,
                "actorType", actorType,
                "deletedAt", deletedAt.toString(),
                "gracePeriodEndsAt", gracePeriodEndsAt.toString(),
                "anonymized", anonymized
        ));
        if (actorId != null) {
            payload.put("actorId", actorId);
        }
        return new AccountDomainEvent("account.deleted", payload);
    }
}
