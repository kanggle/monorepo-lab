package com.example.fanplatform.membership.infrastructure.scheduling;

import com.example.fanplatform.membership.application.SweepExpiredMembershipsUseCase;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drives the membership expiry sweep on a fixed delay (TASK-FAN-BE-014), reusing
 * the service's existing {@code @EnableScheduling}. Each tick invokes
 * {@link SweepExpiredMembershipsUseCase#sweep(int)} for one batch and counts the
 * emitted events ({@code membership_expiry_swept_total}).
 *
 * <p>Disabled via {@code fanplatform.membership.expiry-sweep.enabled=false} (e.g.
 * in integration tests that drive the use case directly to control timing). The
 * default {@code initial-delay} keeps the scheduler quiet during short ITs so it
 * does not interfere with the outbox-relay / subscribe ITs.
 */
@Slf4j
@Component
@ConditionalOnProperty(
        name = "fanplatform.membership.expiry-sweep.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class MembershipExpirySweepScheduler {

    private final SweepExpiredMembershipsUseCase sweepUseCase;
    private final int maxBatch;
    private final Counter sweptCounter;

    public MembershipExpirySweepScheduler(
            SweepExpiredMembershipsUseCase sweepUseCase,
            @Value("${fanplatform.membership.expiry-sweep.max-batch:100}") int maxBatch,
            MeterRegistry meterRegistry) {
        this.sweepUseCase = sweepUseCase;
        this.maxBatch = maxBatch;
        this.sweptCounter = Counter.builder("membership_expiry_swept_total")
                .description("Memberships swept to fan.membership.expired.v1 by the expiry sweeper.")
                .register(meterRegistry);
    }

    @Scheduled(
            fixedDelayString = "${fanplatform.membership.expiry-sweep.interval-ms:60000}",
            initialDelayString = "${fanplatform.membership.expiry-sweep.initial-delay-ms:60000}")
    public void tick() {
        try {
            int swept = sweepUseCase.sweep(maxBatch);
            if (swept > 0) {
                sweptCounter.increment(swept);
            }
        } catch (RuntimeException e) {
            // Best-effort: a transient tick failure (e.g. DB blip) is logged and
            // retried on the next tick — never propagated.
            log.warn("Expiry sweep tick failed: {}", e.toString());
        }
    }
}
