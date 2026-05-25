package com.wms.notification.adapter.outbound.scheduling;

import com.wms.notification.application.port.in.RetryFailedDeliveryUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically scans {@code notification_delivery} for PENDING rows due
 * for retry and dispatches them through the {@link RetryFailedDeliveryUseCase}.
 * Disabled under {@code standalone} (no Kafka, no DB).
 *
 * <p>Two scheduler workers cannot double-fire because the use-case picks
 * rows under {@code SELECT … FOR UPDATE SKIP LOCKED}.
 */
@Component
@Profile("!standalone")
public class DeliveryRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(DeliveryRetryScheduler.class);

    private final RetryFailedDeliveryUseCase retry;

    public DeliveryRetryScheduler(RetryFailedDeliveryUseCase retry) {
        this.retry = retry;
    }

    @Scheduled(fixedDelayString = "${wms.notification.delivery.retry-poll-interval-ms:5000}",
            initialDelayString = "${wms.notification.delivery.retry-initial-delay-ms:5000}")
    public void poll() {
        try {
            int dispatched = retry.dispatchDueRetries();
            if (dispatched > 0) {
                log.debug("Dispatched {} due retries", dispatched);
            }
        } catch (RuntimeException e) {
            log.warn("Retry scheduler tick failed: {}", e.getMessage());
        }
    }
}
