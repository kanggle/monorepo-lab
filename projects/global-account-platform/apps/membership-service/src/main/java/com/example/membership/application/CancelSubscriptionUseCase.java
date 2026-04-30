package com.example.membership.application;

import com.example.membership.application.event.MembershipEventPublisher;
import com.example.membership.application.exception.SubscriptionNotActiveException;
import com.example.membership.application.exception.SubscriptionNotFoundException;
import com.example.membership.application.exception.SubscriptionPermissionDeniedException;
import com.example.membership.domain.subscription.Subscription;
import com.example.membership.domain.subscription.SubscriptionRepository;
import com.example.membership.domain.subscription.status.SubscriptionStatus;
import com.example.membership.domain.subscription.status.SubscriptionStatusMachine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class CancelSubscriptionUseCase {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionStatusHistoryRecorder historyRecorder;
    private final MembershipEventPublisher eventPublisher;
    private final SubscriptionStatusMachine statusMachine = new SubscriptionStatusMachine();

    @Transactional
    public void cancel(String subscriptionId, String requesterAccountId) {
        Subscription s = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new SubscriptionNotFoundException(subscriptionId));

        if (!s.belongsTo(requesterAccountId)) {
            throw new SubscriptionPermissionDeniedException(
                    "Cannot cancel another user's subscription");
        }

        if (!s.isActive()) {
            throw new SubscriptionNotActiveException(subscriptionId);
        }

        SubscriptionStatus from = s.getStatus();
        LocalDateTime now = LocalDateTime.now();
        s.cancel(now, statusMachine);
        subscriptionRepository.save(s);

        historyRecorder.recordTransition(s,
                from, SubscriptionStatus.CANCELLED,
                "USER_CANCEL", "USER", now);

        eventPublisher.publishCancelled(s);
    }
}
