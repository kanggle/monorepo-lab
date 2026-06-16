package com.example.membership.application;

import com.example.membership.application.result.AccessCheckResult;
import com.example.membership.domain.plan.PlanLevel;
import com.example.membership.domain.subscription.Subscription;
import com.example.membership.domain.subscription.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CheckContentAccessUseCase {

    private final SubscriptionRepository subscriptionRepository;

    @Transactional(readOnly = true)
    public AccessCheckResult check(String accountId, PlanLevel requiredPlanLevel) {
        List<Subscription> actives = subscriptionRepository.findActiveByAccountId(accountId);
        PlanLevel highest = actives.stream()
                .map(Subscription::getPlanLevel)
                .max(PlanLevel.BY_RANK)
                .orElse(PlanLevel.FREE);
        boolean allowed = highest.meetsOrExceeds(requiredPlanLevel);
        return new AccessCheckResult(accountId, requiredPlanLevel, allowed, highest);
    }
}
