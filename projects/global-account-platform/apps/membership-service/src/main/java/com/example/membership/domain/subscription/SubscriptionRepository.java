package com.example.membership.domain.subscription;

import com.example.membership.domain.plan.PlanLevel;
import com.example.membership.domain.subscription.status.SubscriptionStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository {

    Subscription save(Subscription subscription);

    Optional<Subscription> findById(String id);

    List<Subscription> findByAccountId(String accountId);

    Optional<Subscription> findActiveByAccountIdAndPlanLevel(String accountId, PlanLevel planLevel);

    List<Subscription> findActiveByAccountId(String accountId);

    /**
     * Finds subscriptions whose status = ACTIVE and expires_at is not null and expires_at <= cutoff.
     * FREE subscriptions (expires_at IS NULL) are excluded.
     */
    List<Subscription> findExpirable(SubscriptionStatus status, LocalDateTime cutoff, int limit);
}
