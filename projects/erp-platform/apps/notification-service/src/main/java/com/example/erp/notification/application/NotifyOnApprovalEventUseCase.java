package com.example.erp.notification.application;

import com.example.erp.notification.application.command.NotifyOnApprovalCommand;
import com.example.erp.notification.application.command.NotifyOnDelegationCommand;
import com.example.erp.notification.application.command.NotifyOnDelegationRevokedCommand;
import com.example.erp.notification.application.port.outbound.ClockPort;
import com.example.erp.notification.application.port.outbound.IdGeneratorPort;
import com.example.erp.notification.application.port.outbound.NotificationChannelPort;
import com.example.erp.notification.application.port.outbound.NotificationMetricsPort;
import com.example.erp.notification.config.ExternalNotificationProperties;
import com.example.erp.notification.domain.delivery.DeliveryChannel;
import com.example.erp.notification.domain.delivery.NotificationDelivery;
import com.example.erp.notification.domain.delivery.repository.NotificationDeliveryRepository;
import com.example.erp.notification.domain.notification.Notification;
import com.example.erp.notification.domain.notification.NotificationType;
import com.example.erp.notification.domain.recipient.Recipient;
import com.example.erp.notification.domain.recipient.RecipientResolver;
import com.example.erp.notification.domain.render.ApprovalEvent;
import com.example.erp.notification.domain.render.DelegationEvent;
import com.example.erp.notification.domain.render.DelegationRevokedEvent;
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
    private final ExternalNotificationProperties externalProperties;

    /** Synchronous v1 channel: IN_APP (the persist itself, immediately DELIVERED). */
    private static final DeliveryChannel V1_CHANNEL = DeliveryChannel.IN_APP;
    /** v2.0 external channel created PENDING + due when external delivery is enabled. */
    private static final DeliveryChannel EXTERNAL_CHANNEL = DeliveryChannel.SLACK;

    public NotifyOnApprovalEventUseCase(RecipientResolver recipientResolver,
                                        NotificationFactory factory,
                                        NotificationRepository notificationRepository,
                                        NotificationDeliveryRepository deliveryRepository,
                                        EventDedupeService dedupeService,
                                        IdGeneratorPort idGenerator,
                                        ClockPort clock,
                                        NotificationMetricsPort metrics,
                                        List<NotificationChannelPort> channelPorts,
                                        ExternalNotificationProperties externalProperties) {
        this.recipientResolver = recipientResolver;
        this.factory = factory;
        this.notificationRepository = notificationRepository;
        this.deliveryRepository = deliveryRepository;
        this.dedupeService = dedupeService;
        this.idGenerator = idGenerator;
        this.clock = clock;
        this.metrics = metrics;
        this.channelPorts = List.copyOf(channelPorts);
        this.externalProperties = externalProperties;
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
        if (skipIfDuplicate(event.eventId(), command.topic())) {
            return;
        }
        Recipient recipient = recipientResolver.resolve(event);
        Instant now = clock.now();
        Notification notification = factory.from(event, recipient,
                idGenerator.newNotificationId(), now);
        dispatch(notification, recipient, event.eventId(), event.type(), event.tenantId(),
                command.topic(), event.approvalRequestId(), now);
    }

    /**
     * Delegation-granted consume boundary (TASK-ERP-BE-014). Same dedupe → resolve
     * → render → persist → deliver → dedupe sequence as the approval handler,
     * sharing {@link #dispatch}; the recipient is the delegate and the dedupe
     * provenance aggregate id is the {@code grantId}.
     */
    @Transactional
    public void handle(NotifyOnDelegationCommand command) {
        DelegationEvent event = command.event();
        if (skipIfDuplicate(event.eventId(), command.topic())) {
            return;
        }
        Recipient recipient = recipientResolver.resolve(event);
        Instant now = clock.now();
        Notification notification = factory.from(event, recipient,
                idGenerator.newNotificationId(), now);
        dispatch(notification, recipient, event.eventId(), event.type(), event.tenantId(),
                command.topic(), event.grantId(), now);
    }

    /**
     * Delegation-revoked consume boundary (TASK-ERP-BE-016). Same sequence as the
     * granted handler (shared {@link #dispatch}); the recipient is the delegate
     * (who loses the authority) and the dedupe provenance aggregate id is the
     * {@code grantId}.
     */
    @Transactional
    public void handle(NotifyOnDelegationRevokedCommand command) {
        DelegationRevokedEvent event = command.event();
        if (skipIfDuplicate(event.eventId(), command.topic())) {
            return;
        }
        Recipient recipient = recipientResolver.resolve(event);
        Instant now = clock.now();
        Notification notification = factory.from(event, recipient,
                idGenerator.newNotificationId(), now);
        dispatch(notification, recipient, event.eventId(), event.type(), event.tenantId(),
                command.topic(), event.grantId(), now);
    }

    private boolean skipIfDuplicate(String eventId, String topic) {
        if (dedupeService.isDuplicate(eventId)) {
            log.debug("Duplicate event skipped: eventId={} topic={}", eventId, topic);
            metrics.dedupeSkipped();
            return true;
        }
        return false;
    }

    /**
     * The shared persist → deliver (IN_APP) → dedupe-provenance → metrics sequence,
     * all within the caller's single {@code @Transactional} boundary (T2 / A7
     * atomicity: no "delivered but not deduped" and no "deduped but not delivered").
     */
    private void dispatch(Notification notification, Recipient recipient, String eventId,
                          NotificationType type, String tenantId, String topic,
                          String sourceAggregateId, Instant now) {
        notificationRepository.save(notification);

        NotificationChannelPort channel = channelFor(V1_CHANNEL);
        NotificationDelivery delivery = NotificationDelivery.createPending(
                idGenerator.newDeliveryId(), tenantId, notification.id(),
                eventId, V1_CHANNEL, now);
        NotificationChannelPort.DeliveryOutcome outcome = channel.deliver(notification);
        if (outcome.delivered()) {
            delivery.markDelivered(now);
        } else {
            // Defensive: the v1 IN_APP adapter always reports delivered; a
            // non-delivered outcome here would only occur if mis-wired.
            delivery.markFailed(outcome.detail(), now);
        }
        deliveryRepository.save(delivery);

        // v2.0 (TASK-ERP-BE-020): when external delivery is enabled, additionally persist a
        // PENDING external (SLACK) delivery — immediately due — for the DeliveryRetryScheduler
        // to attempt asynchronously. The external I/O is NEVER performed here (a slow/failed
        // webhook must not roll back the in-app notification). Default off ⇒ net-zero (no row).
        if (externalProperties.isEnabled()) {
            NotificationDelivery external = NotificationDelivery.createPendingExternal(
                    idGenerator.newDeliveryId(), tenantId, notification.id(),
                    eventId, EXTERNAL_CHANNEL, now);
            deliveryRepository.save(external);
        }

        dedupeService.markProcessed(eventId, topic, sourceAggregateId);

        metrics.dispatched(type);
        metrics.deliveryStatus(delivery.status());
        log.debug("Dispatched notification id={} type={} recipient={} delivery={}",
                notification.id(), type, recipient.employeeId(), delivery.status());
    }
}
