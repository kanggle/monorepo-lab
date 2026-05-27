package com.wms.notification.application.service;

import com.wms.notification.application.port.out.ChannelPort;
import com.wms.notification.application.port.out.DeliveryRepository;
import com.wms.notification.application.port.out.OutboxPort;
import com.wms.notification.domain.delivery.NotificationDelivery;
import com.wms.notification.domain.error.ChannelNotConfiguredException;
import com.wms.notification.domain.error.ChannelPermanentFailureException;
import com.wms.notification.domain.error.DeliveryRetryExhaustedException;
import com.wms.notification.domain.routing.ChannelType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Per-row delivery dispatch — owns the {@code REQUIRES_NEW} transactional
 * scope so the outer scheduler / retry caller can iterate without a single
 * row's failure rolling back already-succeeded siblings.
 *
 * <p>Extracted from {@link DeliveryExecutor} to break Spring AOP
 * self-invocation: when {@code dispatchDueRetries()} called the sibling
 * {@code execute(...)} on {@code this}, the proxy was bypassed and
 * {@code REQUIRES_NEW} was silently ignored. This bean is invoked
 * cross-bean from {@link DeliveryExecutor}, so the proxy is honoured and
 * each {@code dispatch(...)} call runs in its own transaction.
 *
 * <p>Package-private — only {@link DeliveryExecutor} should call it; all
 * external callers go through {@link
 * com.wms.notification.application.port.in.RetryFailedDeliveryUseCase}.
 */
@Component
class DeliveryDispatchPerRow {

    private static final Logger log = LoggerFactory.getLogger(DeliveryDispatchPerRow.class);
    private static final double JITTER_RATIO = 0.2;

    private final DeliveryRepository deliveries;
    private final OutboxPort outbox;
    private final Map<ChannelType, ChannelPort> channelsByType;
    private final List<Integer> backoffSeconds;
    private final Clock clock;

    private final Counter attemptsSucceeded;
    private final Counter attemptsRetried;
    private final Counter attemptsFailed;
    private final Timer deliveryDuration;

    DeliveryDispatchPerRow(DeliveryRepository deliveries,
                           OutboxPort outbox,
                           List<ChannelPort> channelPorts,
                           @Value("${wms.notification.delivery.backoff-seconds:1,5,30,120,600}")
                                   List<Integer> backoffSeconds,
                           Clock clock,
                           MeterRegistry meters) {
        this.deliveries = deliveries;
        this.outbox = outbox;
        this.channelsByType = new HashMap<>();
        for (ChannelPort port : channelPorts) {
            this.channelsByType.put(port.channelType(), port);
        }
        this.backoffSeconds = List.copyOf(backoffSeconds);
        this.clock = clock;
        this.attemptsSucceeded = meters.counter("notification.delivery.attempts", "channel", "any", "status", "SUCCEEDED");
        this.attemptsRetried = meters.counter("notification.delivery.attempts", "channel", "any", "status", "RETRIED");
        this.attemptsFailed = meters.counter("notification.delivery.attempts", "channel", "any", "status", "FAILED");
        this.deliveryDuration = Timer.builder("notification.delivery.duration.seconds")
                .description("Vendor latency for channel send")
                .register(meters);
    }

    /**
     * Dispatch one delivery in its own transaction. Caller is expected to
     * be a different Spring bean so the proxy is honoured and
     * {@code REQUIRES_NEW} is applied.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void dispatch(NotificationDelivery delivery) {
        ChannelPort port = channelsByType.get(ChannelType.SLACK);
        if (port == null) {
            log.error("No channel adapter wired for SLACK; deliveryId={}", delivery.id());
            return;
        }
        MDC.put("deliveryId", delivery.id().toString());
        MDC.put("channelId", delivery.channelId());
        MDC.put("attempt", String.valueOf(delivery.attemptCount() + 1));
        long started = System.nanoTime();
        try {
            port.send(delivery.recipient(), delivery.payloadSnapshot());
            delivery.markSucceeded(clock.instant());
            deliveries.update(delivery);
            outbox.writeDeliveryCompleted(delivery, "SUCCEEDED");
            attemptsSucceeded.increment();
        } catch (ChannelNotConfiguredException permanent) {
            markPermanentAndPersist(delivery, "CHANNEL_NOT_CONFIGURED", permanent, "FAILED_CHANNEL_NOT_CONFIGURED");
        } catch (ChannelPermanentFailureException permanent) {
            markPermanentAndPersist(delivery, "VENDOR_4XX", permanent, "FAILED_PERMANENT");
        } catch (RuntimeException retryable) {
            handleRetryable(delivery, retryable);
        } finally {
            deliveryDuration.record(Duration.ofNanos(System.nanoTime() - started));
            MDC.remove("deliveryId");
            MDC.remove("channelId");
            MDC.remove("attempt");
        }
    }

    private void markPermanentAndPersist(NotificationDelivery delivery,
                                         String reasonPrefix,
                                         Exception cause,
                                         String outboxStatus) {
        delivery.markFailedPermanent(reasonPrefix + ": " + cause.getMessage(), clock.instant());
        deliveries.update(delivery);
        outbox.writeDeliveryCompleted(delivery, outboxStatus);
        attemptsFailed.increment();
    }

    private void handleRetryable(NotificationDelivery delivery, RuntimeException error) {
        Duration backoff = nextBackoff(delivery.attemptCount());
        try {
            delivery.markRetryable(error.getMessage(), backoff, clock.instant());
            deliveries.update(delivery);
            attemptsRetried.increment();
            log.info("Delivery {} attempt {} failed transiently; scheduling retry at {}",
                    delivery.id(), delivery.attemptCount(), delivery.scheduledRetryAt().orElse(null));
        } catch (DeliveryRetryExhaustedException exhausted) {
            // markRetryable already transitioned to FAILED before throwing.
            deliveries.update(delivery);
            outbox.writeDeliveryCompleted(delivery, "FAILED_RETRY_EXHAUSTED");
            attemptsFailed.increment();
            log.warn("Delivery {} exhausted retry budget after {} attempts",
                    delivery.id(), exhausted.attempts());
        }
    }

    /** Exponential backoff with ±20% jitter. Visible to {@link DeliveryExecutor} for test seam delegation. */
    Duration nextBackoff(int attemptZeroBased) {
        int idx = Math.min(attemptZeroBased, backoffSeconds.size() - 1);
        long baseSeconds = backoffSeconds.get(idx);
        double jitter = baseSeconds * JITTER_RATIO;
        double offset = ThreadLocalRandom.current().nextDouble(-jitter, jitter + 1e-9);
        long jitteredMillis = Math.max(0L, (long) ((baseSeconds + offset) * 1000));
        return Duration.ofMillis(jitteredMillis);
    }

    /** Test seam — exposes the configured base value (no jitter). */
    long backoffBaseSeconds(int attemptZeroBased) {
        int idx = Math.min(attemptZeroBased, backoffSeconds.size() - 1);
        return backoffSeconds.get(idx);
    }
}
