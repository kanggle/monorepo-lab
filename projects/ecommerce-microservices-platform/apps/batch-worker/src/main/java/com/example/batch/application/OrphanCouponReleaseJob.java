package com.example.batch.application;

import com.example.batch.domain.model.BatchJobExecution;
import com.example.batch.domain.repository.BatchJobExecutionRepository;
import com.example.batch.infrastructure.client.OrderServiceClient;
import com.example.batch.infrastructure.client.PromotionServiceClient;
import com.example.batch.infrastructure.client.PromotionServiceClient.StaleUsedCoupon;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * Job — release coupons held by orders that never existed (TASK-INT-028).
 *
 * <p>When an order placement rolls back, order-service sends a release to promotion-service. If that
 * release never arrives (promotion-service down, network), nothing else frees the coupon: the
 * cancellation restore path needs an order to fire. The coupon stays {@code USED} until it expires.
 *
 * <p><b>Flow.</b> promotion-service lists coupons {@code USED} longer than the floor → order-service
 * says which of their orders exist in any tenant → only the coupons whose order is absent are
 * released, each with its own tenant.
 *
 * <p><b>The money rules</b> — a wrong release lets a customer use a coupon twice:
 * <ol>
 *   <li><b>Unknown is not absent.</b> If the existence call fails in any way, this run releases
 *       nothing. The client throws on a missing body for the same reason.</li>
 *   <li><b>All-absent brake.</b> If at least {@code minScannedForAllAbsentAbort} coupons were scanned and
 *       not one order exists, the existence target is far more likely an empty or wrong environment
 *       than a real backlog. Nothing is released; the run is {@code FAILED}.</li>
 * </ol>
 *
 * <p>A single release failure does not stop the rest; the coupon is still listed next run. Any other
 * failure records {@code FAILED} and is swallowed so the scheduler thread survives (overview.md
 * invariant 2). Tests call {@link #execute()} directly, bypassing ShedLock.
 */
@Slf4j
@Service
public class OrphanCouponReleaseJob {

    static final String JOB_NAME = "orphanCouponReleaseJob";
    static final String RELEASED_COUNTER_NAME = "batch_orphan_coupons_released_total";
    static final String RELEASE_FAILED_COUNTER_NAME = "batch_orphan_coupon_release_failed_total";
    static final String ABORTED_COUNTER_NAME = "batch_orphan_coupon_runs_aborted_total";

    private final PromotionServiceClient promotionServiceClient;
    private final OrderServiceClient orderServiceClient;
    private final BatchJobExecutionRepository executionRepository;
    private final Counter releasedCounter;
    private final Counter releaseFailedCounter;
    private final Counter abortedCounter;

    @Value("${batch.jobs.orphan-coupon-release.enabled:true}")
    private boolean enabled;

    @Value("${batch.jobs.orphan-coupon-release.older-than-minutes:60}")
    private int olderThanMinutes;

    @Value("${batch.jobs.orphan-coupon-release.limit:200}")
    private int limit;

    @Value("${batch.jobs.orphan-coupon-release.min-scanned-for-all-absent-abort:20}")
    private int minScannedForAllAbsentAbort;

    public OrphanCouponReleaseJob(PromotionServiceClient promotionServiceClient,
                                  OrderServiceClient orderServiceClient,
                                  BatchJobExecutionRepository executionRepository,
                                  MeterRegistry meterRegistry) {
        this.promotionServiceClient = promotionServiceClient;
        this.orderServiceClient = orderServiceClient;
        this.executionRepository = executionRepository;
        this.releasedCounter = meterRegistry.counter(RELEASED_COUNTER_NAME);
        this.releaseFailedCounter = meterRegistry.counter(RELEASE_FAILED_COUNTER_NAME);
        this.abortedCounter = meterRegistry.counter(ABORTED_COUNTER_NAME);
    }

    public void execute() {
        if (!enabled) {
            log.info("orphanCouponReleaseJob is disabled via batch.jobs.orphan-coupon-release.enabled=false; skipping.");
            return;
        }

        BatchJobExecution execution = executionRepository.save(BatchJobExecution.start(JOB_NAME));
        log.info("OrphanCouponReleaseJob started (executionId={})", execution.getId());

        try {
            List<StaleUsedCoupon> coupons = promotionServiceClient.staleUsedCoupons(olderThanMinutes, limit);
            if (coupons.isEmpty()) {
                execution.complete();
                executionRepository.save(execution);
                log.info("OrphanCouponReleaseJob completed (executionId={} scanned=0)", execution.getId());
                return;
            }

            List<String> orderIds = coupons.stream().map(StaleUsedCoupon::orderId).distinct().toList();
            // Throws on any failure or unreadable answer → catch below → FAILED, nothing released.
            Set<String> existing = orderServiceClient.existingOrderIds(orderIds);

            if (existing.isEmpty() && coupons.size() >= minScannedForAllAbsentAbort) {
                abortedCounter.increment();
                throw new IllegalStateException("refusing to release: all " + coupons.size()
                        + " scanned coupons report an absent order — the existence target is likely an empty"
                        + " or wrong environment (brake: batch.jobs.orphan-coupon-release"
                        + ".min-scanned-for-all-absent-abort=" + minScannedForAllAbsentAbort + ")");
            }

            int released = 0;
            int failed = 0;
            for (StaleUsedCoupon coupon : coupons) {
                if (existing.contains(coupon.orderId())) {
                    continue;
                }
                try {
                    promotionServiceClient.release(coupon.couponId(), coupon.orderId(), coupon.tenantId());
                    released++;
                } catch (Exception e) {
                    failed++;
                    log.warn("Orphan coupon release failed — will be retried next run (couponId={} orderId={} tenantId={}): {}",
                            coupon.couponId(), coupon.orderId(), coupon.tenantId(), e.toString());
                }
            }
            if (released > 0) {
                releasedCounter.increment(released);
            }
            if (failed > 0) {
                releaseFailedCounter.increment(failed);
            }

            execution.complete();
            executionRepository.save(execution);
            log.info("OrphanCouponReleaseJob completed (executionId={} scanned={} existing={} released={} failed={})",
                    execution.getId(), coupons.size(), coupons.size() - released - failed, released, failed);

        } catch (Exception e) {
            String errorMsg = (e.getMessage() != null && !e.getMessage().isBlank())
                    ? e.getMessage()
                    : e.getClass().getName();
            execution.fail(errorMsg);
            executionRepository.save(execution);
            log.error("OrphanCouponReleaseJob FAILED (executionId={}): {}", execution.getId(), errorMsg, e);
            // Do NOT re-throw — a failed job must not stop the scheduler thread (overview.md invariant 2).
        }
    }
}
