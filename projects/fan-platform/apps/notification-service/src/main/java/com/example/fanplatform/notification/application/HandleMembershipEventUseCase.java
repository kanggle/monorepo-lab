package com.example.fanplatform.notification.application;

import com.example.common.id.UuidV7;
import com.example.fanplatform.notification.application.consumer.MembershipEvent;
import com.example.fanplatform.notification.application.port.ProcessedEventStore;
import com.example.fanplatform.notification.domain.channel.NotificationChannelPort;
import com.example.fanplatform.notification.domain.notification.Notification;
import com.example.fanplatform.notification.domain.notification.NotificationRepository;
import com.example.fanplatform.notification.domain.notification.NotificationTemplate;
import com.example.fanplatform.notification.domain.notification.NotificationType;
import com.example.fanplatform.notification.domain.time.ClockPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Idempotently turns one membership lifecycle event into one in-app notification
 * + a fan-out across the channel adapters (architecture.md § Consume Semantics).
 *
 * <p><b>Idempotency</b>: the {@code processed_events} guard short-circuits an
 * at-least-once duplicate (no second notification, no second dispatch). The
 * unique {@code source_event_id} column is the DB-level secondary guard — a
 * concurrent duplicate that slips past the check rolls its transaction back and
 * is retried, at which point the guard now sees it processed and skips.
 *
 * <p><b>Atomicity</b>: notification persist + channel dispatch + the
 * {@code processed_events} mark all run in one transaction. A channel adapter
 * that threw would roll the whole unit back; the consumer's
 * {@code DefaultErrorHandler} then retries and finally routes to
 * {@code <topic>.dlq} (emit-not-throw — the partition never stalls). The v1
 * logged mock channels never throw on the happy path.
 */
@Slf4j
@Service
public class HandleMembershipEventUseCase {

    private final NotificationRepository notificationRepository;
    private final ProcessedEventStore processedEvents;
    private final List<NotificationChannelPort> channels;
    private final ClockPort clock;

    public HandleMembershipEventUseCase(NotificationRepository notificationRepository,
                                        ProcessedEventStore processedEvents,
                                        List<NotificationChannelPort> channels,
                                        ClockPort clock) {
        this.notificationRepository = notificationRepository;
        this.processedEvents = processedEvents;
        this.channels = channels;
        this.clock = clock;
    }

    @Transactional
    public void handle(MembershipEvent event) {
        if (processedEvents.alreadyProcessed(event.eventId())) {
            log.debug("Duplicate event skipped (already processed): eventId={}, type={}",
                    event.eventId(), event.eventType());
            return;
        }

        NotificationType type = NotificationType.fromEventType(event.eventType());
        NotificationTemplate.RenderedContent content = render(type, event);

        Notification notification = Notification.create(
                UuidV7.randomString(),
                event.tenantId(),
                event.accountId(),
                type,
                content.title(),
                content.body(),
                event.eventId(),
                event.eventType(),
                event.membershipId(),
                clock.now());

        notificationRepository.save(notification);

        // Best-effort fan-out: the durable inbox row is already written; the
        // deterministic mock channels log + return a synthetic ref.
        for (NotificationChannelPort channel : channels) {
            channel.deliver(notification);
        }

        processedEvents.markProcessed(event.eventId(), event.eventType());
        log.info("Recorded notification: type={}, account={}, membership={}, eventId={}",
                type, event.accountId(), event.membershipId(), event.eventId());
    }

    private static NotificationTemplate.RenderedContent render(NotificationType type, MembershipEvent event) {
        return switch (type) {
            case WELCOME -> NotificationTemplate.welcome(
                    event.tier(), event.planMonths(), event.validFrom(), event.validTo());
            case CANCELLATION -> NotificationTemplate.cancellation(
                    event.tier(), event.canceledAt(), event.reason());
        };
    }
}
