package com.example.scmplatform.demandplanning.adapter.outbound.batch;

import com.example.scmplatform.demandplanning.application.usecase.SweepReorderUseCase;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Nightly batch sweep for below-reorder-point SKUs lacking a fresh alert.
 * ShedLock ensures single-instance execution across replicas (batch-heavy trait).
 * <p>
 * Mirrors IVS {@code StalenessDetectionScheduler} pattern.
 * Lock name: {@code reorder-sweep-batch} — must be consistent across deployments.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReorderSweepScheduler {

    private final SweepReorderUseCase sweepUseCase;
    private final MeterRegistry meterRegistry;

    /**
     * Run nightly at 02:00 UTC. ShedLock holds the lock for at most 30 minutes.
     * Initial delay: 5 minutes after startup so the service is fully warmed.
     */
    @Scheduled(cron = "${demand-planning.sweep.cron:0 0 2 * * *}",
               zone = "UTC")
    @SchedulerLock(
            name = "reorder-sweep-batch",
            lockAtMostFor = "PT30M",
            lockAtLeastFor = "PT5M"
    )
    public void runSweep() {
        log.info("Reorder sweep batch starting");
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            int raised = sweepUseCase.sweep();
            sample.stop(Timer.builder("reorder_batch_sweep_duration_seconds")
                    .description("Duration of reorder sweep batch run")
                    .register(meterRegistry));
            log.info("Reorder sweep batch completed: suggestionsRaised={}", raised);
        } catch (Exception e) {
            log.error("Reorder sweep batch failed: {}", e.getMessage(), e);
            // Do not rethrow — let ShedLock release and the next run retry
        }
    }
}
