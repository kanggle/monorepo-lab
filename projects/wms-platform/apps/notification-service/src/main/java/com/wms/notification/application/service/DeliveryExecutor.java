package com.wms.notification.application.service;

import com.wms.notification.application.port.inbound.RetryFailedDeliveryUseCase;
import com.wms.notification.application.port.outbound.ChannelPort;
import com.wms.notification.application.port.outbound.DeliveryRepository;
import com.wms.notification.application.port.outbound.OutboxPort;
import com.wms.notification.adapter.outbound.slack.ChannelNotConfiguredException;
import com.wms.notification.adapter.outbound.slack.SlackPermanentFailureException;
import com.wms.notification.domain.delivery.DeliveryStatus;
import com.wms.notification.domain.delivery.NotificationDelivery;
import com.wms.notification.domain.error.DeliveryRetryExhaustedException;
import com.wms.notification.domain.routing.ChannelType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dispatches each PENDING delivery to its channel adapter, then transitions
 * the delivery state machine based on the adapter outcome. Each call is
 * its own transaction — the routing service has already committed the
 * dedupe + delivery + outbox rows.
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

    public DeliveryExecutor(DeliveryRepository deliveries,
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

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
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
        execute(delivery);
    }

    @Override
    @Transactional
    public int dispatchDueRetries() {
        Instant now = clock.instant();
        List<NotificationDelivery> due = deliveries.findAndLockPendingDueForRetry(now, 50);
        for (NotificationDelivery delivery : due) {
            execute(delivery);
        }
        return due.size();
    }

    /** Inline dispatch used by the post-commit hook on the routing service. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void execute(NotificationDelivery delivery) {
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
            delivery.markFailedPermanent("CHANNEL_NOT_CONFIGURED: " + permanent.getMessage(), clock.instant());
            deliveries.update(delivery);
            outbox.writeDeliveryCompleted(delivery, "FAILED_CHANNEL_NOT_CONFIGURED");
            attemptsFailed.increment();
        } catch (SlackPermanentFailureException permanent) {
            delivery.markFailedPermanent("VENDOR_4XX: " + permanent.getMessage(), clock.instant());
            deliveries.update(delivery);
            outbox.writeDeliveryCompleted(delivery, "FAILED_PERMANENT");
            attemptsFailed.increment();
        } catch (RuntimeException retryable) {
            handleRetryable(delivery, retryable);
        } finally {
            deliveryDuration.record(Duration.ofNanos(System.nanoTime() - started));
            MDC.remove("deliveryId");
            MDC.remove("channelId");
            MDC.remove("attempt");
        }
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

    /** Exponential backoff with ±20% jitter. Public for test reflection. */
    public Duration nextBackoff(int attemptZeroBased) {
        int idx = Math.min(attemptZeroBased, backoffSeconds.size() - 1);
        long baseSeconds = backoffSeconds.get(idx);
        double jitter = baseSeconds * JITTER_RATIO;
        double offset = ThreadLocalRandom.current().nextDouble(-jitter, jitter + 1e-9);
        long jitteredMillis = Math.max(0L, (long) ((baseSeconds + offset) * 1000));
        return Duration.ofMillis(jitteredMillis);
    }

    /** Test seam — checked by {@code DeliveryExecutorRetryArithmeticTest}. */
    long backoffBaseSeconds(int attemptZeroBased) {
        int idx = Math.min(attemptZeroBased, backoffSeconds.size() - 1);
        return backoffSeconds.get(idx);
    }

    /** Test seam — required by {@code DeliveryStatus} enum check. */
    public DeliveryStatus statusOf(NotificationDelivery delivery) {
        return delivery.status();
    }
}
