package com.example.membership.application;

import com.example.membership.domain.subscription.Subscription;
import com.example.membership.domain.subscription.SubscriptionStatusHistoryEntry;
import com.example.membership.domain.subscription.SubscriptionStatusHistoryRepository;
import com.example.membership.domain.subscription.status.SubscriptionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Shared status-transition audit-recording helper for subscription use-cases.
 *
 * <p>Centralizes the duplicated multi-line block previously inlined in
 * {@code ActivateSubscriptionUseCase}, {@code CancelSubscriptionUseCase}, and
 * {@code ExpireSubscriptionUseCase}: build a {@link SubscriptionStatusHistoryEntry}
 * from the subscription identifiers and transition metadata, then append it to the
 * audit repository.
 *
 * <p>Package-private by design — only application-layer use-cases in this package
 * consume it. Same extraction pattern as {@code PostAccessGuard} in
 * community-service.
 */
@Component
@RequiredArgsConstructor
class SubscriptionStatusHistoryRecorder {

    private final SubscriptionStatusHistoryRepository historyRepository;

    /**
     * Appends a status-transition audit entry for the given subscription.
     *
     * @param subscription  subscription whose id and accountId identify the entry
     * @param from          previous status before the transition
     * @param to            new status after the transition
     * @param operationCode reason/operation code (e.g. {@code USER_SUBSCRIBE},
     *                      {@code USER_CANCEL}, {@code SCHEDULED_EXPIRE})
     * @param actorType     actor responsible for the transition
     *                      ({@code USER} or {@code SYSTEM})
     * @param occurredAt    transition timestamp
     */
    void recordTransition(Subscription subscription,
                          SubscriptionStatus from,
                          SubscriptionStatus to,
                          String operationCode,
                          String actorType,
                          LocalDateTime occurredAt) {
        historyRepository.append(new SubscriptionStatusHistoryEntry(
                subscription.getId(),
                subscription.getAccountId(),
                from,
                to,
                operationCode,
                actorType,
                occurredAt));
    }
}
