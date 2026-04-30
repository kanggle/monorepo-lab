package com.example.membership.application;

import com.example.membership.application.result.MySubscriptionsResult;
import com.example.membership.application.result.SubscriptionResult;
import com.example.membership.domain.plan.PlanLevel;
import com.example.membership.domain.subscription.Subscription;
import com.example.membership.domain.subscription.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GetMySubscriptionsUseCase {

    private final SubscriptionRepository subscriptionRepository;

    @Transactional(readOnly = true)
    public MySubscriptionsResult getMine(String accountId) {
        List<Subscription> all = subscriptionRepository.findByAccountId(accountId);
        List<SubscriptionResult> subs = all.stream()
                .map(SubscriptionResult::from)
                .toList();

        PlanLevel activeLevel = all.stream()
                .filter(Subscription::isActive)
                .map(Subscription::getPlanLevel)
                .max((a, b) -> Integer.compare(a.getRank(), b.getRank()))
                .orElse(PlanLevel.FREE);

        return new MySubscriptionsResult(accountId, subs, activeLevel);
    }
}
