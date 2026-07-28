package com.example.fanplatform.membership.infrastructure.scheduling;

import com.example.fanplatform.membership.application.billing.AutoRenewMembershipsUseCase;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drives the billing-key auto-renewal on a fixed delay (TASK-FAN-BE-033, ADR-002
 * §D2), reusing the service's existing {@code @EnableScheduling}. Each tick invokes
 * {@link AutoRenewMembershipsUseCase#runOnce(int)} for one batch and counts the
 * memberships renewed ({@code membership_auto_renewed_total}) — mirroring the
 * expiry sweeper's scheduler exactly.
 *
 * <p>Disabled via {@code fanplatform.membership.auto-renew.enabled=false} (e.g. in
 * integration tests that drive the use case directly to control timing, exactly
 * like the expiry sweeper). The default {@code initial-delay} keeps the scheduler
 * quiet during short ITs so it does not interfere with other ITs. The default
 * interval is daily (ADR-002 §D2 "매일 배치").
 */
@Slf4j
@Component
@ConditionalOnProperty(
        name = "fanplatform.membership.auto-renew.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class BillingKeyAutoRenewalScheduler {

    private final AutoRenewMembershipsUseCase autoRenewUseCase;
    private final int maxBatch;
    private final Counter renewedCounter;

    public BillingKeyAutoRenewalScheduler(
            AutoRenewMembershipsUseCase autoRenewUseCase,
            @Value("${fanplatform.membership.auto-renew.max-batch:100}") int maxBatch,
            MeterRegistry meterRegistry) {
        this.autoRenewUseCase = autoRenewUseCase;
        this.maxBatch = maxBatch;
        this.renewedCounter = Counter.builder("membership_auto_renewed_total")
                .description("Memberships auto-renewed by charging a stored billing key.")
                .register(meterRegistry);
    }

    @Scheduled(
            fixedDelayString = "${fanplatform.membership.auto-renew.interval-ms:86400000}",
            initialDelayString = "${fanplatform.membership.auto-renew.initial-delay-ms:60000}")
    public void tick() {
        try {
            int renewed = autoRenewUseCase.runOnce(maxBatch);
            if (renewed > 0) {
                renewedCounter.increment(renewed);
            }
        } catch (RuntimeException e) {
            // Best-effort: a transient tick failure (e.g. DB blip) is logged and retried on
            // the next tick — never propagated. Never log the billing key.
            log.warn("Auto-renew tick failed: {}", e.toString());
        }
    }
}
