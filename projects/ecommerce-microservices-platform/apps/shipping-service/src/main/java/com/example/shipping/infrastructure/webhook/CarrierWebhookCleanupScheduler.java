package com.example.shipping.infrastructure.webhook;

import com.example.shipping.application.service.CarrierWebhookCleanupService;
import com.example.shipping.application.service.CarrierWebhookCleanupService.CleanupResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drives the carrier-webhook dedup retention/cleanup sweep (TASK-BE-361 / ADR-007 D5-4):
 * periodically invokes {@link CarrierWebhookCleanupService#sweep()} so the BE-294
 * {@code processed_carrier_webhooks} idempotency table does not grow unbounded.
 *
 * <p><b>Default OFF (net-zero, AC-5).</b> The bean is gated by
 * {@code @ConditionalOnProperty(shipping.carrier.webhook.cleanup.enabled=true)} — the default
 * ({@code enabled=false}) means the bean is never created and the sweep never runs, so existing
 * behaviour is byte-identical. Operators should enable it in any long-lived deploy.
 *
 * <p><b>Single-instance (ShedLock).</b> {@code @SchedulerLock} ensures only one replica executes
 * a given tick (the others skip on the held {@code shipping-carrier-webhook-cleanup} lock).
 *
 * <p><b>ShedLock test trap.</b> Business behaviour is verified by calling
 * {@link CarrierWebhookCleanupService#sweep()} directly (NOT by waiting on this tick):
 * {@code lockAtLeastFor} would let a test run only the first invocation and silently no-op the
 * rest. This bean is the thin scheduling + locking shell; the use-case holds the logic.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "shipping.carrier.webhook.cleanup.enabled", havingValue = "true")
public class CarrierWebhookCleanupScheduler {

    private final CarrierWebhookCleanupService carrierWebhookCleanupService;

    @Scheduled(fixedDelayString = "${shipping.carrier.webhook.cleanup.fixed-delay-ms:3600000}")
    @SchedulerLock(
            name = "shipping-carrier-webhook-cleanup",
            lockAtMostFor = "PT10M",
            lockAtLeastFor = "PT5S"
    )
    public void runCleanup() {
        try {
            CleanupResult result = carrierWebhookCleanupService.sweep();
            log.debug("Carrier-webhook dedup cleanup tick complete: {}", result);
        } catch (Exception e) {
            // Best-effort: never let a sweep failure escape the scheduled thread (ShedLock
            // releases on return; the next tick retries).
            log.error("Carrier-webhook dedup cleanup tick failed: {}", e.getMessage(), e);
        }
    }
}
