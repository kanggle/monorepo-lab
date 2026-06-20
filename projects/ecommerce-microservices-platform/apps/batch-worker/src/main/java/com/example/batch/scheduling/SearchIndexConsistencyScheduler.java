package com.example.batch.scheduling;

import com.example.batch.application.SearchIndexConsistencyJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Thin scheduling shell for {@link SearchIndexConsistencyJob} (TASK-BE-409 / AC-4).
 *
 * <p>Fires daily at 03:00 UTC ({@code cron="0 0 3 * * *"}) and acquires the
 * {@code batch-search-index-consistency-check} ShedLock to guarantee that only one replica
 * executes the check per tick ({@code platform/service-types/batch-job.md} — "분산락 필수").
 *
 * <p><b>Responsibilities of this class:</b> scheduling trigger + ShedLock acquisition only.
 * All business logic lives in {@link SearchIndexConsistencyJob#execute()} — that method is
 * also directly callable from tests (bypassing ShedLock) to avoid the
 * {@code lockAtLeastFor} trap that would silently no-op subsequent test invocations within
 * the lock window.
 *
 * <p>Mirrors {@code AutoCollectTrackingScheduler} in shipping-service (TASK-BE-360).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SearchIndexConsistencyScheduler {

    private final SearchIndexConsistencyJob searchIndexConsistencyJob;

    @Scheduled(cron = "0 0 3 * * *")
    @SchedulerLock(
            name = "batch-search-index-consistency-check",
            lockAtMostFor = "PT30M",
            lockAtLeastFor = "PT5S"
    )
    public void runConsistencyCheck() {
        log.info("SearchIndexConsistencyScheduler: acquiring lock and delegating to job");
        searchIndexConsistencyJob.execute();
    }
}
