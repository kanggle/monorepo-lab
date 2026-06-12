package com.example.shipping.infrastructure.carrier;

import com.example.shipping.application.service.AutoCollectTrackingService;
import com.example.shipping.application.service.AutoCollectTrackingService.SweepResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drives the unattended auto-collect tracking sweep (TASK-BE-360 / ADR-007 D5-3): periodically
 * invokes {@link AutoCollectTrackingService#sweep()} so in-flight shipments converge to their
 * latest carrier status even without an operator pull or a (lost/delayed) aggregator webhook.
 *
 * <p><b>Default OFF (net-zero, AC-3).</b> The bean is gated by
 * {@code @ConditionalOnProperty(shipping.carrier.auto-collect.enabled=true)} — the default
 * ({@code enabled=false}) means the bean is never created and the scheduler never runs, so
 * existing admin-driven behaviour is byte-identical. With {@code mode=mock} + blank
 * {@code mock-status}, even an enabled sweep is a real no-op (the carrier port returns empty).
 *
 * <p><b>Single-instance (AC-2).</b> {@code @SchedulerLock} ensures only one replica executes a
 * given tick (the others skip on the held {@code shipping-auto-collect-tracking} lock).
 *
 * <p><b>ShedLock test trap.</b> Business behaviour is verified by calling
 * {@link AutoCollectTrackingService#sweep()} directly (NOT by waiting on this tick):
 * {@code lockAtLeastFor} would let a test run only the first invocation and silently no-op the
 * rest. This bean is the thin scheduling + locking shell; the use-case holds the logic.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "shipping.carrier.auto-collect.enabled", havingValue = "true")
public class AutoCollectTrackingScheduler {

    private final AutoCollectTrackingService autoCollectTrackingService;

    @Scheduled(fixedDelayString = "${shipping.carrier.auto-collect.fixed-delay-ms:60000}")
    @SchedulerLock(
            name = "shipping-auto-collect-tracking",
            lockAtMostFor = "PT5M",
            lockAtLeastFor = "PT5S"
    )
    public void runSweep() {
        try {
            SweepResult result = autoCollectTrackingService.sweep();
            log.debug("Auto-collect tracking tick complete: {}", result);
        } catch (Exception e) {
            // Best-effort: never let a sweep failure escape the scheduled thread (ShedLock
            // releases on return; the next tick retries). Per-item failures are already
            // isolated inside sweep().
            log.error("Auto-collect tracking sweep tick failed: {}", e.getMessage(), e);
        }
    }
}
