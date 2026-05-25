package com.wms.notification.application.service;

import com.wms.notification.application.port.in.RetryFailedDeliveryUseCase;
import com.wms.notification.domain.delivery.NotificationDelivery;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Bridges {@link AlertRoutingService} (which queues delivery rows) and
 * {@link com.wms.notification.application.service.DeliveryExecutor}
 * (which actually calls the channel adapter). Architectural rule §3:
 * channel ports are async at the application boundary — the transactional
 * commit completes before the adapter hits Slack.
 *
 * <p>The routing service registers a {@link TransactionSynchronization}
 * via {@link #scheduleAfterCommit(NotificationDelivery)}; on commit, the
 * dispatcher invokes {@link RetryFailedDeliveryUseCase#retry(UUID)} in a
 * fresh transaction.
 *
 * <p>If no transaction is active (defensive — should never happen for
 * @KafkaListener-driven calls), the dispatch is performed inline.
 */
@Component
public class PostCommitDeliveryDispatcher {

    private static final Logger log = LoggerFactory.getLogger(PostCommitDeliveryDispatcher.class);

    private final RetryFailedDeliveryUseCase retry;

    public PostCommitDeliveryDispatcher(RetryFailedDeliveryUseCase retry) {
        this.retry = retry;
    }

    public void scheduleAfterCommit(NotificationDelivery delivery) {
        UUID id = delivery.id();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    safeDispatch(id);
                }
            });
        } else {
            log.debug("No active TX synchronization; dispatching inline for delivery={}", id);
            safeDispatch(id);
        }
    }

    private void safeDispatch(UUID id) {
        try {
            retry.retry(id);
        } catch (RuntimeException e) {
            // Swallow — the row remains PENDING with scheduledRetryAt = null,
            // so the retry scheduler will pick it up on the next tick.
            log.warn("Post-commit dispatch failed for delivery={}: {}", id, e.getMessage());
        }
    }
}
