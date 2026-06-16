package com.example.account.application.service;

import com.example.account.application.event.AccountEventPublisher;
import com.example.account.domain.account.Account;
import com.example.account.domain.status.AccountStatus;

import java.time.Instant;

/**
 * Shared outbox-event dispatch for an account status change.
 *
 * <p>A status change always emits {@code account.status.changed}, and additionally
 * emits {@code account.locked} when the target is {@code LOCKED} or
 * {@code account.unlocked} when transitioning back to {@code ACTIVE} from
 * {@code LOCKED}. This three-way fan-out was duplicated across
 * {@link AccountStatusUseCase} (operator-driven change) and
 * {@link ProvisionStatusChangeUseCase} (provisioning-driven change); centralising it
 * here keeps the event semantics in one place.
 *
 * <p>The {@code previousStatus == targetStatus} no-op guard is intentionally left to
 * the caller: {@code AccountStatusUseCase} suppresses events on a same-status change,
 * whereas the provisioning path relies on the state machine to reject same-status
 * transitions before reaching this dispatch. Keeping the guard out of this helper
 * preserves both behaviours exactly.
 */
final class AccountStatusEvents {

    private AccountStatusEvents() {
    }

    static void publishStatusChangeEvents(AccountEventPublisher eventPublisher,
                                          Account account,
                                          AccountStatus previousStatus,
                                          AccountStatus targetStatus,
                                          String reasonName,
                                          String actorType,
                                          String actorId,
                                          Instant now) {
        String tenantId = account.getTenantId().value();

        eventPublisher.publishStatusChanged(
                account, tenantId, previousStatus.name(), reasonName, actorType, actorId, now);

        if (targetStatus == AccountStatus.LOCKED) {
            eventPublisher.publishAccountLocked(
                    account, tenantId, reasonName, actorType, actorId, now);
        } else if (targetStatus == AccountStatus.ACTIVE && previousStatus == AccountStatus.LOCKED) {
            eventPublisher.publishAccountUnlocked(
                    account, tenantId, reasonName, actorType, actorId, now);
        }
    }
}
