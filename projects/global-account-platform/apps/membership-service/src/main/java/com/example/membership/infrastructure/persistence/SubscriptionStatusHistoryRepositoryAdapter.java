package com.example.membership.infrastructure.persistence;

import com.example.membership.domain.subscription.SubscriptionStatusHistoryEntry;
import com.example.membership.domain.subscription.SubscriptionStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SubscriptionStatusHistoryRepositoryAdapter implements SubscriptionStatusHistoryRepository {

    private final SubscriptionStatusHistoryJpaRepository jpa;

    @Override
    public void append(SubscriptionStatusHistoryEntry entry) {
        jpa.save(SubscriptionStatusHistoryJpaEntity.from(entry));
    }
}
