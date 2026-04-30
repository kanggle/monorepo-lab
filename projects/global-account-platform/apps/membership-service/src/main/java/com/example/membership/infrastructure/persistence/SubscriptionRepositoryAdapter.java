package com.example.membership.infrastructure.persistence;

import com.example.membership.domain.plan.PlanLevel;
import com.example.membership.domain.subscription.Subscription;
import com.example.membership.domain.subscription.SubscriptionRepository;
import com.example.membership.domain.subscription.status.SubscriptionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class SubscriptionRepositoryAdapter implements SubscriptionRepository {

    private final SubscriptionJpaRepository jpa;

    @Override
    public Subscription save(Subscription subscription) {
        SubscriptionJpaEntity entity = jpa.findById(subscription.getId())
                .map(existing -> {
                    existing.updateFromDomain(subscription);
                    return existing;
                })
                .orElseGet(() -> SubscriptionJpaEntity.fromDomain(subscription));
        SubscriptionJpaEntity saved = jpa.save(entity);
        return saved.toDomain();
    }

    @Override
    public Optional<Subscription> findById(String id) {
        return jpa.findById(id).map(SubscriptionJpaEntity::toDomain);
    }

    @Override
    public List<Subscription> findByAccountId(String accountId) {
        return jpa.findByAccountId(accountId).stream()
                .map(SubscriptionJpaEntity::toDomain)
                .toList();
    }

    @Override
    public Optional<Subscription> findActiveByAccountIdAndPlanLevel(String accountId, PlanLevel planLevel) {
        return jpa.findByAccountIdAndPlanLevelAndStatus(accountId, planLevel, SubscriptionStatus.ACTIVE)
                .map(SubscriptionJpaEntity::toDomain);
    }

    @Override
    public List<Subscription> findActiveByAccountId(String accountId) {
        return jpa.findByAccountIdAndStatus(accountId, SubscriptionStatus.ACTIVE).stream()
                .map(SubscriptionJpaEntity::toDomain)
                .toList();
    }

    @Override
    public List<Subscription> findExpirable(SubscriptionStatus status, LocalDateTime cutoff, int limit) {
        return jpa.findExpirable(status, cutoff, PageRequest.of(0, limit)).stream()
                .map(SubscriptionJpaEntity::toDomain)
                .toList();
    }
}
