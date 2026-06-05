package com.example.membership.application;

import com.example.membership.application.event.MembershipEventPublisher;
import com.example.membership.application.exception.SubscriptionNotFoundException;
import com.example.membership.domain.subscription.Subscription;
import com.example.membership.domain.subscription.SubscriptionRepository;
import com.example.membership.domain.subscription.status.SubscriptionStatus;
import com.example.membership.domain.subscription.status.SubscriptionStatusMachine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExpireSubscriptionUseCase {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionStatusHistoryRecorder historyRecorder;
    private final MembershipEventPublisher eventPublisher;
    private final SubscriptionStatusMachine statusMachine = new SubscriptionStatusMachine();

    /**
     * Expires a single subscription in its own transaction. Idempotent:
     * already non-ACTIVE subscriptions are skipped.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void expire(String subscriptionId) {
        Subscription s = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new SubscriptionNotFoundException(subscriptionId));

        if (s.getStatus() != SubscriptionStatus.ACTIVE) {
            log.debug("Skipping expiry for non-ACTIVE subscription {} (status={})",
                    subscriptionId, s.getStatus());
            return;
        }

        SubscriptionStatus from = s.getStatus();
        LocalDateTime now = LocalDateTime.now();
        s.expire(now, statusMachine);
        subscriptionRepository.save(s);

        historyRecorder.recordTransition(s,
                from, SubscriptionStatus.EXPIRED,
                "SCHEDULED_EXPIRE", "SYSTEM", now);

        eventPublisher.publishExpired(s);
    }
}
