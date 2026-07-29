package com.example.fanplatform.notification.application;

import com.example.common.id.UuidV7;
import com.example.fanplatform.notification.application.consumer.CommunityEvent;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Idempotently turns one community interaction event into zero, one, or several
 * in-app notifications + a fan-out across the channel adapters (TASK-FAN-BE-026).
 * Kept separate from {@link HandleMembershipEventUseCase} because the two event
 * families carry different domain shapes and different recipient-resolution rules;
 * only the idempotency + persist + dispatch mechanics are common.
 *
 * <p><b>Recipient routing is purely payload-driven.</b> The enriched event carries
 * {@code postAuthorAccountId} + {@code mentionedAccountIds}
 * (community-events.md § Recipient-routing fields), so this service makes NO
 * synchronous call to community-service — the platform's no-sync-coupling
 * invariant for notification-service (task AC-1).
 *
 * <p><b>Zero-notification outcomes are successes, not failures.</b> Each of the
 * following logs and still marks the event processed (no DLQ, no retry):
 * <ul>
 *   <li><b>Self-notify suppression</b> (AC-3) — the commenter is the post author,
 *       or the reactor is the post author. Nobody is notified about their own
 *       action. A self-mention is suppressed the same way.</li>
 *   <li><b>Missing {@code postAuthorAccountId}</b> — an in-flight event emitted
 *       before the producer enrichment. It has no addressable recipient; that is a
 *       rollout artifact, not a malformed event, so it is skipped rather than
 *       routed to the DLQ (task § Edge Cases).</li>
 *   <li><b>Empty {@code mentionedAccountIds}</b> — the normal case today (the
 *       producer has no mention syntax). No mention alert, no error.</li>
 * </ul>
 *
 * <p><b>Idempotency</b> (AC-4): the same {@code processed_events} guard as the
 * membership path short-circuits an at-least-once duplicate. The composite unique
 * {@code (source_event_id, account_id, type)} (V3) is the DB-level secondary
 * guard — composite rather than {@code source_event_id} alone precisely because a
 * single comment event may fan out to a REPLY plus one MENTION per mentioned
 * account.
 *
 * <p><b>Atomicity</b>: every notification persist + channel dispatch + the
 * {@code processed_events} mark run in one transaction, exactly as on the
 * membership path.
 */
@Slf4j
@Service
public class HandleCommunityEventUseCase {

    private final NotificationRepository notificationRepository;
    private final ProcessedEventStore processedEvents;
    private final List<NotificationChannelPort> channels;
    private final ClockPort clock;

    public HandleCommunityEventUseCase(NotificationRepository notificationRepository,
                                       ProcessedEventStore processedEvents,
                                       List<NotificationChannelPort> channels,
                                       ClockPort clock) {
        this.notificationRepository = notificationRepository;
        this.processedEvents = processedEvents;
        this.channels = channels;
        this.clock = clock;
    }

    @Transactional
    public void handle(CommunityEvent event) {
        if (processedEvents.alreadyProcessed(event.eventId())) {
            log.debug("Duplicate event skipped (already processed): eventId={}, type={}",
                    event.eventId(), event.eventType());
            return;
        }

        List<Notification> notifications = switch (event.eventType()) {
            case NotificationType.EVENT_COMMENT_ADDED -> commentNotifications(event);
            case NotificationType.EVENT_REACTION_ADDED -> reactionNotifications(event);
            default -> throw new IllegalArgumentException(
                    "Unsupported community event type: " + event.eventType());
        };

        for (Notification notification : notifications) {
            notificationRepository.save(notification);
            // Best-effort fan-out: the durable inbox row is already written.
            for (NotificationChannelPort channel : channels) {
                channel.deliver(notification);
            }
        }

        processedEvents.markProcessed(event.eventId(), event.eventType());
        if (notifications.isEmpty()) {
            log.info("No addressable recipient for community event: type={}, post={}, eventId={}"
                            + " — skipped (marked processed)",
                    event.eventType(), event.postId(), event.eventId());
        } else {
            log.info("Recorded {} community notification(s): type={}, post={}, eventId={}",
                    notifications.size(), event.eventType(), event.postId(), event.eventId());
        }
    }

    /** REPLY to the post author + one MENTION per (non-self, deduped) mentioned account. */
    private List<Notification> commentNotifications(CommunityEvent event) {
        Instant now = clock.now();
        List<Notification> result = new ArrayList<>();

        String postAuthor = event.postAuthorAccountId();
        if (postAuthor == null) {
            log.info("comment.added without postAuthorAccountId (pre-enrichment event):"
                    + " no reply alert. eventId={}", event.eventId());
        } else if (postAuthor.equals(event.actorAccountId())) {
            log.debug("Self-notify suppressed: commenter is the post author. eventId={}",
                    event.eventId());
        } else {
            NotificationTemplate.RenderedContent content =
                    NotificationTemplate.reply(event.actorAccountId(), event.postId());
            result.add(notification(event, postAuthor, NotificationType.REPLY, content, now));
        }

        // Deduped so a payload repeating the same account cannot violate the
        // (source_event_id, account_id, type) unique constraint.
        for (String mentioned : new LinkedHashSet<>(event.mentionedAccountIds())) {
            if (mentioned.equals(event.actorAccountId())) {
                log.debug("Self-mention suppressed: account={}, eventId={}",
                        mentioned, event.eventId());
                continue;
            }
            NotificationTemplate.RenderedContent content =
                    NotificationTemplate.mention(event.actorAccountId(), event.postId());
            result.add(notification(event, mentioned, NotificationType.MENTION, content, now));
        }
        return result;
    }

    /** REACTION_BADGE to the post author, unless the reactor IS the post author. */
    private List<Notification> reactionNotifications(CommunityEvent event) {
        String postAuthor = event.postAuthorAccountId();
        if (postAuthor == null) {
            log.info("reaction.added without postAuthorAccountId (pre-enrichment event):"
                    + " no badge alert. eventId={}", event.eventId());
            return List.of();
        }
        if (postAuthor.equals(event.actorAccountId())) {
            log.debug("Self-notify suppressed: reactor is the post author. eventId={}",
                    event.eventId());
            return List.of();
        }
        NotificationTemplate.RenderedContent content = NotificationTemplate.reactionBadge(
                event.actorAccountId(), event.reactionType(), event.postId());
        return List.of(notification(event, postAuthor, NotificationType.REACTION_BADGE,
                content, clock.now()));
    }

    private static Notification notification(CommunityEvent event, String recipientAccountId,
                                             NotificationType type,
                                             NotificationTemplate.RenderedContent content,
                                             Instant now) {
        return Notification.create(
                UuidV7.randomString(),
                event.tenantId(),
                recipientAccountId,
                type,
                content.title(),
                content.body(),
                event.eventId(),
                event.eventType(),
                null,            // membershipId — community-sourced notification
                event.postId(),  // the correlating aggregate instead
                now);
    }
}
