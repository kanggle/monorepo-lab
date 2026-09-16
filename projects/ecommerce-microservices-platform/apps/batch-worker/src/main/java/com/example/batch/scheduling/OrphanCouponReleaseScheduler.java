package com.example.batch.scheduling;

import com.example.batch.application.OrphanCouponReleaseJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Thin scheduling shell for {@link OrphanCouponReleaseJob} (TASK-INT-028).
 *
 * <p>Fires every 30 minutes at :05 and :35 — offset from the 10-minute confirm-paid-stale sweep — and
 * holds the {@code batch-orphan-coupon-release} ShedLock so only one replica releases per tick. All
 * logic lives in {@link OrphanCouponReleaseJob#execute()}. Mirrors {@link StalePaidOrderConfirmationScheduler}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrphanCouponReleaseScheduler {

    private final OrphanCouponReleaseJob orphanCouponReleaseJob;

    @Scheduled(cron = "0 5,35 * * * *")
    @SchedulerLock(
            name = "batch-orphan-coupon-release",
            lockAtMostFor = "PT25M",
            lockAtLeastFor = "PT5S"
    )
    public void runRelease() {
        log.info("OrphanCouponReleaseScheduler: acquiring lock and delegating to job");
        orphanCouponReleaseJob.execute();
    }
}
