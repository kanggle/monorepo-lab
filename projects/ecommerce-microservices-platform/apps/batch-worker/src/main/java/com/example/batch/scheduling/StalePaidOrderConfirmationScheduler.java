package com.example.batch.scheduling;

import com.example.batch.application.StalePaidOrderConfirmationJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Thin scheduling shell for {@link StalePaidOrderConfirmationJob} (TASK-BE-413 / AC-1).
 *
 * <p>Fires every 10 minutes and acquires the {@code batch-stale-paid-order-confirmation}
 * ShedLock to guarantee that only one replica executes the sweep per tick
 * ({@code platform/service-types/batch-job.md} -- "분산락 필수").
 *
 * <p><b>Responsibilities of this class:</b> scheduling trigger and ShedLock acquisition only.
 * All business logic lives in {@link StalePaidOrderConfirmationJob#execute()} -- that method
 * is also directly callable from tests (bypassing ShedLock) to avoid the
 * {@code lockAtLeastFor} trap that would silently no-op subsequent test invocations within
 * the lock window.
 *
 * <p>Mirrors {@link SearchIndexConsistencyScheduler} (TASK-BE-409).
 *
 * <p>{@code @EnableScheduling} is already present on
 * {@link com.example.batch.BatchWorkerApplication} (re-added by TASK-BE-409);
 * {@code SchedulerConfig} wires the ShedLock JDBC provider (BE-409) -- no new config needed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StalePaidOrderConfirmationScheduler {

    private final StalePaidOrderConfirmationJob stalePaidOrderConfirmationJob;

    @Scheduled(cron = "0 */10 * * * *")
    @SchedulerLock(
            name = "batch-stale-paid-order-confirmation",
            lockAtMostFor = "PT9M",
            lockAtLeastFor = "PT5S"
    )
    public void runConfirmation() {
        log.info("StalePaidOrderConfirmationScheduler: acquiring lock and delegating to job");
        stalePaidOrderConfirmationJob.execute();
    }
}
