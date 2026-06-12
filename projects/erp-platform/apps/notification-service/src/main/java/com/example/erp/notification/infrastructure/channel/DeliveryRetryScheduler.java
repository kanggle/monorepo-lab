package com.example.erp.notification.infrastructure.channel;

import com.example.erp.notification.application.RetryDeliveryService;
import com.example.erp.notification.application.port.outbound.ClockPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Category C external-delivery retry scheduler (TASK-ERP-BE-020, ADR-MONO-005 § D5). Active
 * only when {@code erpplatform.notification.external.retry.enabled=true} (absent by default =
 * net-zero, no scheduled work). A single {@code fixedDelay} tick is <b>non-reentrant</b>
 * (Spring runs one invocation at a time per instance), so within one instance ticks never
 * overlap; multi-instance optimistic-lock enforcement (the {@code version} column / ShedLock)
 * is a documented follow-on.
 *
 * <p>Delegates to {@link RetryDeliveryService#runDue} so an integration test can drive a sweep
 * directly (bypassing the scheduler) for determinism.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "erpplatform.notification.external.retry.enabled", havingValue = "true")
public class DeliveryRetryScheduler {

    private final RetryDeliveryService retryDeliveryService;
    private final ClockPort clock;

    public DeliveryRetryScheduler(RetryDeliveryService retryDeliveryService, ClockPort clock) {
        this.retryDeliveryService = retryDeliveryService;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${erpplatform.notification.external.retry.poll-interval-ms:5000}")
    public void tick() {
        int attempted = retryDeliveryService.runDue(clock.now());
        if (attempted > 0) {
            log.debug("DeliveryRetryScheduler processed {} due external delivery(ies)", attempted);
        }
    }
}
