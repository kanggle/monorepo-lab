package com.example.erp.notification.application;

import com.example.erp.notification.application.command.NotifyOnApprovalCommand;
import com.example.erp.notification.application.port.outbound.ClockPort;
import com.example.erp.notification.application.port.outbound.IdGeneratorPort;
import com.example.erp.notification.application.port.outbound.NotificationChannelPort;
import com.example.erp.notification.application.port.outbound.NotificationMetricsPort;
import com.example.erp.notification.domain.delivery.DeliveryChannel;
import com.example.erp.notification.domain.delivery.NotificationDelivery;
import com.example.erp.notification.domain.delivery.repository.NotificationDeliveryRepository;
import com.example.erp.notification.domain.notification.Notification;
import com.example.erp.notification.domain.recipient.Recipient;
import com.example.erp.notification.domain.recipient.RecipientResolver;
import com.example.erp.notification.domain.render.ApprovalEvent;
import com.example.erp.notification.domain.notification.repository.NotificationRepository;
import com.example.erp.notification.domain.render.NotificationFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * The single {@code @Transactional} consume boundary (architecture.md § Layer
 * Structure). For one approval event: dedupe check → resolve recipient → render
 * → persist {@code Notification} → create + deliver {@code NotificationDelivery}
 * (IN_APP: PENDING → DELIVERED synchronously, attempt_count=1) → record dedupe
 * provenance — all in ONE transaction (T2 / A7 atomicity: no "delivered but not
 * deduped" and no "deduped but not delivered").
 *
 * <p><b>Terminal consumer</b>: this use case publishes / re-emits NOTHING. The
 * delivery is the in-app persist; the {@link NotificationChannelPort} is the
 * only outbound delivery boundary (E5-adjacent — no outbox).
 */
@Slf4j
@Service
public class NotifyOnApprovalEventUseCase {

    private final RecipientResolver recipientResolver;
    private final NotificationFactory factory;
    private final NotificationRepository notificationRepository;
    private final NotificationDeliveryRepository deliveryRepository;
    private final EventDedupeService dedupeService;
    private final IdGeneratorPort idGenerator;
    private final ClockPort clock;
    private final NotificationMetricsPort metrics;
    private final List<NotificationChannelPort> channelPorts;

    /** v1 channel: IN_APP only (external channels are v2 — green-wash forbidden). */
    private static final DeliveryChannel V1_CHANNEL = DeliveryChannel.IN_APP;

    public NotifyOnApprovalEventUseCase(RecipientResolver recipientResolver,
                                        NotificationFactory factory,
                                        NotificationRepository notificationRepository,
                                        NotificationDeliveryRepository deliveryRepository,
                                        EventDedupeService dedupeService,
                                        IdGeneratorPort idGenerator,
                                        ClockPort clock,
                                        NotificationMetricsPort metrics,
                                        List<NotificationChannelPort> channelPorts) {
        this.recipientResolver = recipientResolver;
        this.factory = factory;
        this.notificationRepository = notificationRepository;
        this.deliveryRepository = deliveryRepository;
        this.dedupeService = dedupeService;
        this.idGenerator = idGenerator;
        this.clock = clock;
        this.metrics = metrics;
        this.channelPorts = List.copyOf(channelPorts);
    }

    private NotificationChannelPort channelFor(DeliveryChannel channel) {
        return channelPorts.stream()
                .filter(p -> p.channel() == channel)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No channel adapter for " + channel));
    }

    @Transactional
    public void handle(NotifyOnApprovalCommand command) {
        ApprovalEvent event = command.event();
        if (dedupeService.isDuplicate(event.eventId())) {
            log.debug("Duplicate approval event skipped: eventId={} topic={}",
                    event.eventId(), command.topic());
            metrics.dedupeSkipped();
            return;
        }

        Recipient recipient = recipientResolver.resolve(event);
        Instant now = clock.now();
        String notificationId = idGenerator.newNotificationId();
        Notification notification = factory.from(event, recipient, notificationId, now);
        notificationRepository.save(notification);

        NotificationChannelPort channel = channelFor(V1_CHANNEL);
        NotificationDelivery delivery = NotificationDelivery.createPending(
                idGenerator.newDeliveryId(), event.tenantId(), notificationId,
                event.eventId(), V1_CHANNEL, now);
        NotificationChannelPort.DeliveryOutcome outcome = channel.deliver(notification);
        if (outcome.delivered()) {
            delivery.markDelivered(now);
        } else {
            // Defensive: the v1 IN_APP adapter always reports delivered; a
            // non-delivered outcome here would only occur if mis-wired.
            delivery.markFailed(outcome.detail(), now);
        }
        deliveryRepository.save(delivery);

        dedupeService.markProcessed(event.eventId(), command.topic(), event.approvalRequestId());

        metrics.dispatched(event.type());
        metrics.deliveryStatus(delivery.status());
        log.debug("Dispatched notification id={} type={} recipient={} delivery={}",
                notificationId, event.type(), recipient.employeeId(), delivery.status());
    }
}
