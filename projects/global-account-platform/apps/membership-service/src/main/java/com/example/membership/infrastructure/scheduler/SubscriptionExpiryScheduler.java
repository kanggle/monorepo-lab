package com.example.membership.infrastructure.scheduler;

import com.example.membership.application.ExpireSubscriptionUseCase;
import com.example.membership.domain.subscription.Subscription;
import com.example.membership.domain.subscription.SubscriptionRepository;
import com.example.membership.domain.subscription.status.SubscriptionStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Scans ACTIVE subscriptions with expires_at <= now and expires them one by one.
 * Each subscription is expired in its own transaction (REQUIRES_NEW) so that one
 * failure does not block the batch. FREE subscriptions (expires_at IS NULL) are excluded.
 */
@Slf4j
@Component
@Profile("!standalone")
public class SubscriptionExpiryScheduler {

    private final SubscriptionRepository subscriptionRepository;
    private final ExpireSubscriptionUseCase expireSubscriptionUseCase;
    private final int batchSize;

    public SubscriptionExpiryScheduler(SubscriptionRepository subscriptionRepository,
                                       ExpireSubscriptionUseCase expireSubscriptionUseCase,
                                       @Value("${membership.scheduler.expiry-batch-size:100}") int batchSize) {
        this.subscriptionRepository = subscriptionRepository;
        this.expireSubscriptionUseCase = expireSubscriptionUseCase;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${membership.scheduler.expiry-fixed-delay-ms:3600000}")
    public void expireSubscriptions() {
        LocalDateTime cutoff = LocalDateTime.now();
        List<Subscription> toExpire = subscriptionRepository.findExpirable(
                SubscriptionStatus.ACTIVE, cutoff, batchSize);
        if (toExpire.isEmpty()) {
            return;
        }
        log.info("Expiry scheduler: processing {} ACTIVE subscriptions", toExpire.size());
        for (Subscription s : toExpire) {
            try {
                expireSubscriptionUseCase.expire(s.getId());
            } catch (Exception e) {
                log.error("Failed to expire subscription {}: {}", s.getId(), e.getMessage());
            }
        }
    }
}
