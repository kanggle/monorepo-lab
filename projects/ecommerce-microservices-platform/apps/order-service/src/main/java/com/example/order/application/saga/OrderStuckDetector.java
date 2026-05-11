package com.example.order.application.saga;

import com.example.order.application.port.OrderMetricsPort;
import com.example.order.domain.model.Order;
import com.example.order.domain.repository.OrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Order saga stuck-detector (TASK-BE-138, ADR-MONO-005 Category A).
 *
 * <p>The choreographed {@code order.placed → payment.completed → order CONFIRMED}
 * flow has no orchestrator row. When payment-service fails to consume / process
 * {@code OrderPlaced} for any reason, the corresponding {@code orders} row stays
 * in {@code PENDING + payment_id IS NULL} indefinitely without operator
 * notification. This sweeper queries those rows once they exceed the configured
 * grace period and dispatches per-order recovery to {@link OrderStuckRecoveryHandler}.
 *
 * <p><b>Cross-bean delegation</b>: per-order work runs in a separate
 * {@link OrderStuckRecoveryHandler} bean so the {@code REQUIRES_NEW}
 * {@code @Transactional} call goes through the Spring AOP proxy. Calling a
 * private {@code @Transactional} method on {@code this} would silently fall
 * back to the calling thread's TX — the regression captured in memory
 * {@code feedback_refactor_code_baseline_it.md}. Same split as wms
 * {@code SagaSweeper} + {@code SagaRecoveryHandler} (TASK-BE-050).
 *
 * <p>Disabled in {@code standalone} (no DB / Kafka) and {@code test} profiles
 * (so unit/integration tests can drive {@link #sweepOnce()} directly).
 */
@Slf4j
@Component
@Profile("!standalone & !test")
@ConditionalOnProperty(name = "order.saga.stuck-detector.enabled",
        havingValue = "true", matchIfMissing = true)
public class OrderStuckDetector {

    private final OrderRepository orderRepository;
    private final OrderStuckRecoveryHandler recoveryHandler;
    private final OrderMetricsPort metrics;
    private final Clock clock;

    private final long graceSeconds;
    private final int batchSize;
    private final int maxAttempts;

    public OrderStuckDetector(OrderRepository orderRepository,
                              OrderStuckRecoveryHandler recoveryHandler,
                              OrderMetricsPort metrics,
                              Clock clock,
                              @Value("${order.saga.stuck-detector.threshold-seconds:1800}") long graceSeconds,
                              @Value("${order.saga.stuck-detector.batch-size:100}") int batchSize,
                              @Value("${order.saga.stuck-detector.max-attempts:5}") int maxAttempts) {
        this.orderRepository = orderRepository;
        this.recoveryHandler = recoveryHandler;
        this.metrics = metrics;
        this.clock = clock;
        this.graceSeconds = graceSeconds;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
    }

    @Scheduled(fixedDelayString = "${order.saga.stuck-detector.fixed-delay-ms:60000}",
            initialDelayString = "${order.saga.stuck-detector.initial-delay-ms:30000}")
    public void sweep() {
        metrics.recordStuckDetectorRun();
        Instant cutoff = Instant.now(clock).minus(Duration.ofSeconds(graceSeconds));
        List<Order> stuck;
        try {
            stuck = orderRepository.findStuckPaymentPending(cutoff, batchSize);
        } catch (Exception e) {
            log.error("order_stuck_detector_findStuck_failed reason={}", e.toString(), e);
            return;
        }
        if (stuck.isEmpty()) {
            return;
        }
        log.info("order_stuck_detector_batch count={} cutoff={}", stuck.size(), cutoff);
        for (Order order : stuck) {
            try {
                recoveryHandler.recover(order.getOrderId(), maxAttempts);
            } catch (Exception e) {
                // Per-order failure isolated. Next tick retries.
                log.warn("order_stuck_detector_order_failed orderId={} reason={}",
                        order.getOrderId(), e.toString());
            }
        }
    }

    /** Visible for tests so the IT harness can drive a single tick on demand. */
    public void sweepOnce() {
        sweep();
    }

    /** Visible for tests so the IT harness can read the configured cap. */
    public int maxAttempts() {
        return maxAttempts;
    }

    /** Visible for tests so the IT harness can read the configured grace. */
    public long graceSeconds() {
        return graceSeconds;
    }
}
