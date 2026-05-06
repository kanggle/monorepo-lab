package com.example.scmplatform.inventoryvisibility.adapter.outbound.batch;

import com.example.scmplatform.inventoryvisibility.application.service.InventoryVisibilityApplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Staleness detection batch — batch-heavy trait FIRST CODE in scm-platform.
 * <p>
 * Runs every 5 minutes (fixedDelay). ShedLock ensures only one instance runs
 * across a clustered deployment (Failure Scenario F in TASK-SCM-BE-003).
 * <p>
 * ShedLock relies on the {@code shedlock} table in Postgres (V1__init.sql).
 * Lock name: {@code staleness-detection-batch} — must match across deployments.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StalenessDetectionScheduler {

    private final InventoryVisibilityApplicationService applicationService;

    @Value("${scmplatform.oauth2.required-tenant-id:scm}")
    private String tenantId;

    /**
     * Detect stale nodes and publish SNAPSHOT_STALE alerts every 5 minutes.
     * <p>
     * {@code lockAtMostFor}: maximum 10 minutes — prevents a stuck lock from
     * blocking the next run indefinitely.
     * {@code lockAtLeastFor}: minimum 4 minutes — avoids thundering herd on
     * fast-completing runs in clusters.
     */
    @Scheduled(fixedDelayString = "${inventory-visibility.staleness.check-interval-ms:300000}",
               initialDelayString = "${inventory-visibility.staleness.initial-delay-ms:60000}")
    @SchedulerLock(
            name = "staleness-detection-batch",
            lockAtMostFor = "PT10M",
            lockAtLeastFor = "PT4M"
    )
    public void detectStaleNodes() {
        log.info("Starting staleness detection batch for tenantId={}", tenantId);
        long startMs = System.currentTimeMillis();
        try {
            applicationService.detectAndAlertStaleNodes(tenantId);
            long durationMs = System.currentTimeMillis() - startMs;
            log.info("Staleness detection batch completed in {}ms for tenantId={}",
                    durationMs, tenantId);
        } catch (Exception e) {
            log.error("Staleness detection batch failed for tenantId={}: {}", tenantId, e.getMessage(), e);
            // Do not rethrow — let ShedLock release and the next run try again
        }
    }
}
