package com.example.account.application.event;

import com.example.account.domain.account.Account;

import java.time.Instant;
import java.util.List;

/**
 * Port for account-service lifecycle event publishing (TASK-BE-451 — outbox
 * v1 → v2).
 *
 * <p>Previously a concrete {@code BaseEventPublisher} subclass that wrote to the
 * shared lib {@code outbox} table via {@code OutboxWriter.saveEvent} (FLAT wire —
 * no canonical envelope). It is now a port; the implementation
 * {@link com.example.account.infrastructure.outbox.OutboxAccountEventPublisher}
 * builds the SAME flat payload (via {@link AccountEventFactory}) and persists an
 * {@code account_outbox} row driven by the v2 {@code AbstractOutboxPublisher} relay.
 *
 * <p><b>Wire shape preserved (FLAT, NOT a 7-field envelope).</b> TASK-BE-422/423
 * locked the flat top-level shape — the ecommerce account.* consumers parse
 * root-level fields. The v2 adapter reproduces the EXACT v1 bytes; do NOT wrap.
 *
 * <p>Every method signature (incl. the tenantId-required guard contract) is
 * preserved verbatim from the v1 concrete class so no call site changes.
 */
public interface AccountEventPublisher {

    void publishAccountCreated(Account account, String tenantId, String locale);

    void publishStatusChanged(Account account, String tenantId, String previousStatus,
                              String reasonCode, String actorType, String actorId,
                              Instant occurredAt);

    void publishAccountLocked(Account account, String tenantId, String reasonCode,
                              String actorType, String actorId, Instant lockedAt);

    void publishAccountUnlocked(Account account, String tenantId, String reasonCode,
                                String actorType, String actorId, Instant unlockedAt);

    void publishAccountDeleted(Account account, String tenantId, String reasonCode,
                               String actorType, String actorId,
                               Instant deletedAt, Instant gracePeriodEndsAt);

    /**
     * TASK-BE-231 / TASK-BE-255 — emitted when the provisioning API mutates an
     * account's role set. Carries both {@code beforeRoles} and {@code afterRoles}
     * plus the {@code changedBy} attribution; the legacy {@code roles} payload field
     * aliases {@code afterRoles}.
     */
    void publishRolesChanged(Account account, String tenantId,
                             List<String> beforeRoles,
                             List<String> afterRoles,
                             String changedBy,
                             String actorType, String actorId,
                             Instant occurredAt);

    void publishAccountDeletedAnonymized(Account account, String tenantId, String reasonCode,
                                         String actorType, String actorId,
                                         Instant deletedAt, Instant gracePeriodEndsAt);
}
