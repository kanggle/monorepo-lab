package com.wms.notification.application.service;

import com.wms.notification.application.port.in.RetryFailedDeliveryUseCase;
import com.wms.notification.application.port.out.ChannelPort;
import com.wms.notification.application.port.out.DeliveryRepository;
import com.wms.notification.application.port.out.OutboxPort;
import com.wms.notification.domain.delivery.DeliveryStatus;
import com.wms.notification.domain.delivery.NotificationDelivery;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Picks PENDING deliveries (scheduled retries or post-commit fresh rows)
 * and forwards each to {@link DeliveryDispatchPerRow#dispatch(NotificationDelivery)}.
 *
 * <h2>Why a separate per-row bean?</h2>
 *
 * <p>Per-row dispatch is annotated {@code @Transactional(REQUIRES_NEW)} so
 * that one failing delivery does not roll back already-succeeded sibling
 * rows in the same scheduler tick. Earlier the dispatch body lived on
 * this class and was self-invoked from {@link #dispatchDueRetries()} /
 * {@link #retry(UUID)} — Spring AOP only honours {@code @Transactional}
 * on cross-bean calls, so the self-invocation silently ran the dispatch
 * inside the outer transaction. Splitting the dispatch into
 * {@link DeliveryDispatchPerRow} restores the documented isolation.
 *
 * <h2>Retry budget</h2>
 *
 * <p>{@code 1s → 5s → 30s → 2m → 10m}, capped, ±20% jitter
 * (architecture.md § Retry budget). Configurable via
 * {@code wms.notification.delivery.backoff-seconds}.
 */
@Service
public class DeliveryExecutor implements RetryFailedDeliveryUseCase {

    private static final Logger log = LoggerFactory.getLogger(DeliveryExecutor.class);

    private final DeliveryRepository deliveries;
    private final Clock clock;
    private final DeliveryDispatchPerRow perRow;

    public DeliveryExecutor(DeliveryRepository deliveries,
                            OutboxPort outbox,
                            List<ChannelPort> channelPorts,
                            @Value("${wms.notification.delivery.backoff-seconds:1,5,30,120,600}")
                                    List<Integer> backoffSeconds,
                            Clock clock,
                            MeterRegistry meters) {
        this(deliveries, clock,
                new DeliveryDispatchPerRow(deliveries, outbox, channelPorts, backoffSeconds, clock, meters));
    }

    /**
     * Production wiring — Spring prefers this constructor (marked
     * {@link Autowired @Autowired}) because {@link DeliveryDispatchPerRow}
     * is available as a {@code @Component}. The legacy constructor
     * above is retained only for unit-test wiring that builds the
     * executor directly with fakes.
     */
    @Autowired
    public DeliveryExecutor(DeliveryRepository deliveries,
                            Clock clock,
                            DeliveryDispatchPerRow perRow) {
        this.deliveries = deliveries;
        this.clock = clock;
        this.perRow = perRow;
    }

    @Override
    @Transactional
    public void retry(UUID deliveryId) {
        NotificationDelivery delivery = deliveries.findById(deliveryId).orElse(null);
        if (delivery == null) {
            log.warn("retry called for unknown deliveryId={}", deliveryId);
            return;
        }
        if (delivery.isTerminal()) {
            log.debug("retry skipped — delivery {} already in terminal {}", deliveryId, delivery.status());
            return;
        }
        perRow.dispatch(delivery);
    }

    @Override
    @Transactional
    public int dispatchDueRetries() {
        Instant now = clock.instant();
        List<NotificationDelivery> due = deliveries.findAndLockPendingDueForRetry(now, 50);
        for (NotificationDelivery delivery : due) {
            perRow.dispatch(delivery);
        }
        return due.size();
    }

    /**
     * Test seam — preserved as a 1-line delegation so existing
     * {@code DeliveryExecutorTest} can drive the dispatch path directly.
     * Production callers go through {@link #retry(UUID)} or
     * {@link #dispatchDueRetries()} which invoke {@link DeliveryDispatchPerRow}
     * via the proxy (so {@code REQUIRES_NEW} is honoured).
     */
    public void execute(NotificationDelivery delivery) {
        perRow.dispatch(delivery);
    }

    /** Test seam — exponential backoff with ±20% jitter. */
    public Duration nextBackoff(int attemptZeroBased) {
        return perRow.nextBackoff(attemptZeroBased);
    }

    /** Test seam — checked by {@code DeliveryExecutorRetryArithmeticTest}. */
    long backoffBaseSeconds(int attemptZeroBased) {
        return perRow.backoffBaseSeconds(attemptZeroBased);
    }

    /** Test seam — required by {@code DeliveryStatus} enum check. */
    public DeliveryStatus statusOf(NotificationDelivery delivery) {
        return delivery.status();
    }
}
