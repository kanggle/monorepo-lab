package com.example.erp.notification.application;

import com.example.erp.notification.application.port.outbound.NotificationChannelPort;
import com.example.erp.notification.application.port.outbound.NotificationMetricsPort;
import com.example.erp.notification.domain.delivery.NotificationDelivery;
import com.example.erp.notification.domain.delivery.repository.NotificationDeliveryRepository;
import com.example.erp.notification.domain.notification.Notification;
import com.example.erp.notification.domain.notification.repository.NotificationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Processes one external-channel delivery attempt (TASK-ERP-BE-020) in its <b>own</b>
 * transaction (a separate bean from {@link RetryDeliveryService} so the {@code @Transactional}
 * proxy applies per delivery — one delivery's outcome commits independently of the others,
 * and external I/O is isolated from the Kafka consume transaction).
 *
 * <p>Load the delivery → render-load its notification → attempt the external channel
 * (best-effort, never throws) → drive the Category C machine: {@code markDelivered} on a 2xx,
 * else {@code markRetryable} with the exponential/jitter backoff (terminal {@code FAILED} +
 * {@code DELIVERY_RETRY_EXHAUSTED} at the cap). A missing notification at attempt time is a
 * permanent failure (cannot render), not a crash.
 */
@Slf4j
@Component
public class DeliveryAttemptProcessor {

    private static final String EXHAUSTED_PREFIX = "DELIVERY_RETRY_EXHAUSTED: ";

    private final NotificationDeliveryRepository deliveryRepository;
    private final NotificationRepository notificationRepository;
    private final RetryBackoffPolicy backoffPolicy;
    private final NotificationMetricsPort metrics;
    private final List<NotificationChannelPort> channelPorts;

    public DeliveryAttemptProcessor(NotificationDeliveryRepository deliveryRepository,
                                    NotificationRepository notificationRepository,
                                    RetryBackoffPolicy backoffPolicy,
                                    NotificationMetricsPort metrics,
                                    List<NotificationChannelPort> channelPorts) {
        this.deliveryRepository = deliveryRepository;
        this.notificationRepository = notificationRepository;
        this.backoffPolicy = backoffPolicy;
        this.metrics = metrics;
        this.channelPorts = List.copyOf(channelPorts);
    }

    @Transactional
    public void attempt(String deliveryId, Instant now) {
        Optional<NotificationDelivery> found = deliveryRepository.findById(deliveryId);
        if (found.isEmpty()) {
            return;
        }
        NotificationDelivery delivery = found.get();
        if (delivery.isTerminal()) {
            return;
        }

        Notification notification = notificationRepository
                .findByIdInternal(delivery.tenantId(), delivery.notificationId())
                .orElse(null);
        if (notification == null) {
            delivery.markFailed("notification row missing — cannot render external delivery", now);
            deliveryRepository.save(delivery);
            metrics.deliveryStatus(delivery.status());
            log.warn("Delivery {} has no notification row; marked FAILED", deliveryId);
            return;
        }

        NotificationChannelPort.DeliveryOutcome outcome = channelFor(delivery).deliver(notification);
        if (outcome.delivered()) {
            delivery.markDelivered(now);
        } else {
            boolean willExhaust = delivery.attemptCount() + 1 >= delivery.maxAttempts();
            String error = willExhaust ? EXHAUSTED_PREFIX + outcome.detail() : outcome.detail();
            Duration backoff = backoffPolicy.backoffFor(delivery.attemptCount() + 1);
            delivery.markRetryable(error, backoff, now);
        }

        deliveryRepository.save(delivery);
        if (delivery.isTerminal()) {
            metrics.deliveryStatus(delivery.status());
        }
        log.debug("External delivery {} attempt → status={} attempts={}",
                deliveryId, delivery.status(), delivery.attemptCount());
    }

    private NotificationChannelPort channelFor(NotificationDelivery delivery) {
        return channelPorts.stream()
                .filter(p -> p.channel() == delivery.channel())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No channel adapter for " + delivery.channel()));
    }
}
