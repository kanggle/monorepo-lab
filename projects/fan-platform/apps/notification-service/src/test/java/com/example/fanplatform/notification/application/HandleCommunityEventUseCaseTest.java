package com.example.fanplatform.notification.application;

import com.example.fanplatform.notification.application.consumer.CommunityEvent;
import com.example.fanplatform.notification.application.port.ProcessedEventStore;
import com.example.fanplatform.notification.domain.channel.NotificationChannelPort;
import com.example.fanplatform.notification.domain.notification.Notification;
import com.example.fanplatform.notification.domain.notification.NotificationRepository;
import com.example.fanplatform.notification.domain.notification.NotificationStatus;
import com.example.fanplatform.notification.domain.notification.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TASK-FAN-BE-026 — community interaction events → REPLY / MENTION /
 * REACTION_BADGE notifications, with self-notify suppression (AC-3), eventId
 * dedupe (AC-4) and graceful handling of pre-enrichment in-flight events.
 */
class HandleCommunityEventUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-07-22T09:00:00Z");
    private static final Instant OCCURRED = Instant.parse("2026-07-22T08:59:00Z");
    private static final String TENANT = "fan-platform";

    private NotificationRepository repository;
    private ProcessedEventStore processedEvents;
    private RecordingChannel email;
    private RecordingChannel push;
    private HandleCommunityEventUseCase useCase;

    @BeforeEach
    void setUp() {
        repository = mock(NotificationRepository.class);
        processedEvents = mock(ProcessedEventStore.class);
        email = new RecordingChannel("EMAIL");
        push = new RecordingChannel("PUSH");
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        useCase = new HandleCommunityEventUseCase(
                repository, processedEvents, List.of(email, push), () -> NOW);
    }

    private static CommunityEvent commentAdded(String eventId, String commenter,
                                               String postAuthor, List<String> mentioned) {
        return new CommunityEvent(eventId, NotificationType.EVENT_COMMENT_ADDED, TENANT,
                "post-1", "cmt-1", commenter, postAuthor, mentioned, null, OCCURRED);
    }

    private static CommunityEvent reactionAdded(String eventId, String reactor, String postAuthor) {
        return new CommunityEvent(eventId, NotificationType.EVENT_REACTION_ADDED, TENANT,
                "post-1", null, reactor, postAuthor, List.of(), "LIKE", OCCURRED);
    }

    private List<Notification> saved() {
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        return captor.getAllValues();
    }

    @Test
    @DisplayName("comment.added → REPLY to the post author, post-correlated, no membershipId")
    void commentCreatesReply() {
        when(processedEvents.alreadyProcessed("evt-c1")).thenReturn(false);

        useCase.handle(commentAdded("evt-c1", "fan-1", "author-1", List.of()));

        List<Notification> rows = saved();
        assertThat(rows).hasSize(1);
        Notification reply = rows.get(0);
        assertThat(reply.getType()).isEqualTo(NotificationType.REPLY);
        assertThat(reply.getAccountId()).isEqualTo("author-1");
        assertThat(reply.getTenantId()).isEqualTo(TENANT);
        assertThat(reply.getTitle()).isEqualTo("New reply on your post");
        assertThat(reply.getBody()).contains("fan-1").contains("post-1");
        assertThat(reply.getStatus()).isEqualTo(NotificationStatus.UNREAD);
        assertThat(reply.getPostId()).isEqualTo("post-1");
        assertThat(reply.getMembershipId()).isNull();
        assertThat(reply.getSourceEventId()).isEqualTo("evt-c1");
        assertThat(reply.getSourceEventType()).isEqualTo("community.comment.added");
        assertThat(reply.getCreatedAt()).isEqualTo(NOW);
        assertThat(email.count.get()).isEqualTo(1);
        assertThat(push.count.get()).isEqualTo(1);
        verify(processedEvents).markProcessed("evt-c1", "community.comment.added");
    }

    @Test
    @DisplayName("comment.added with mentions → one MENTION per mentioned account, plus the REPLY")
    void commentCreatesMentionPerAccount() {
        when(processedEvents.alreadyProcessed("evt-c2")).thenReturn(false);

        useCase.handle(commentAdded("evt-c2", "fan-1", "author-1",
                List.of("fan-2", "fan-3")));

        List<Notification> rows = saved();
        assertThat(rows).hasSize(3);
        assertThat(rows).extracting(Notification::getType)
                .containsExactly(NotificationType.REPLY,
                        NotificationType.MENTION, NotificationType.MENTION);
        assertThat(rows).extracting(Notification::getAccountId)
                .containsExactly("author-1", "fan-2", "fan-3");
        assertThat(rows.get(1).getTitle()).isEqualTo("You were mentioned in a comment");
        // Every row shares the source eventId — the composite unique
        // (source_event_id, account_id, type) is what makes the fan-out legal.
        assertThat(rows).allSatisfy(n -> assertThat(n.getSourceEventId()).isEqualTo("evt-c2"));
        assertThat(email.count.get()).isEqualTo(3);
        verify(processedEvents).markProcessed("evt-c2", "community.comment.added");
    }

    @Test
    @DisplayName("duplicate mentioned accounts are deduped (one MENTION row per account)")
    void duplicateMentionsAreDeduped() {
        when(processedEvents.alreadyProcessed("evt-c3")).thenReturn(false);

        useCase.handle(commentAdded("evt-c3", "fan-1", "author-1",
                List.of("fan-2", "fan-2")));

        List<Notification> rows = saved();
        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(Notification::getAccountId)
                .containsExactly("author-1", "fan-2");
    }

    @Test
    @DisplayName("empty mentionedAccountIds → no mention alert, not an error")
    void emptyMentionListIsNotAnError() {
        when(processedEvents.alreadyProcessed("evt-c4")).thenReturn(false);

        useCase.handle(commentAdded("evt-c4", "fan-1", "author-1", List.of()));

        assertThat(saved()).hasSize(1);
        verify(processedEvents).markProcessed("evt-c4", "community.comment.added");
    }

    @Test
    @DisplayName("AC-3: commenter IS the post author → no REPLY, still marked processed")
    void selfReplyIsSuppressed() {
        when(processedEvents.alreadyProcessed("evt-c5")).thenReturn(false);

        useCase.handle(commentAdded("evt-c5", "author-1", "author-1", List.of()));

        verify(repository, never()).save(any());
        assertThat(email.count.get()).isZero();
        assertThat(push.count.get()).isZero();
        verify(processedEvents).markProcessed("evt-c5", "community.comment.added");
    }

    @Test
    @DisplayName("AC-3 spirit: a self-mention is suppressed, other mentions still fire")
    void selfMentionIsSuppressed() {
        when(processedEvents.alreadyProcessed("evt-c6")).thenReturn(false);

        useCase.handle(commentAdded("evt-c6", "fan-1", "author-1",
                List.of("fan-1", "fan-2")));

        List<Notification> rows = saved();
        assertThat(rows).extracting(Notification::getAccountId)
                .containsExactly("author-1", "fan-2");
    }

    @Test
    @DisplayName("reaction.added → REACTION_BADGE to the post author with the reaction type")
    void reactionCreatesBadge() {
        when(processedEvents.alreadyProcessed("evt-r1")).thenReturn(false);

        useCase.handle(reactionAdded("evt-r1", "fan-1", "author-1"));

        List<Notification> rows = saved();
        assertThat(rows).hasSize(1);
        Notification badge = rows.get(0);
        assertThat(badge.getType()).isEqualTo(NotificationType.REACTION_BADGE);
        assertThat(badge.getAccountId()).isEqualTo("author-1");
        assertThat(badge.getTitle()).isEqualTo("Someone reacted to your post");
        assertThat(badge.getBody()).contains("LIKE").contains("fan-1");
        assertThat(badge.getPostId()).isEqualTo("post-1");
        assertThat(badge.getMembershipId()).isNull();
        verify(processedEvents).markProcessed("evt-r1", "community.reaction.added");
    }

    @Test
    @DisplayName("AC-3: reactor IS the post author → no REACTION_BADGE, still marked processed")
    void selfReactionIsSuppressed() {
        when(processedEvents.alreadyProcessed("evt-r2")).thenReturn(false);

        useCase.handle(reactionAdded("evt-r2", "author-1", "author-1"));

        verify(repository, never()).save(any());
        assertThat(push.count.get()).isZero();
        verify(processedEvents).markProcessed("evt-r2", "community.reaction.added");
    }

    @Test
    @DisplayName("AC-4: duplicate delivery (already processed) is a no-op — no row, no dispatch, no re-mark")
    void duplicateIsNoOp() {
        when(processedEvents.alreadyProcessed("evt-c1")).thenReturn(true);

        useCase.handle(commentAdded("evt-c1", "fan-1", "author-1", List.of("fan-2")));

        verify(repository, never()).save(any());
        assertThat(email.count.get()).isZero();
        assertThat(push.count.get()).isZero();
        verify(processedEvents, never()).markProcessed(eq("evt-c1"), any());
    }

    @Test
    @DisplayName("pre-enrichment comment event (no postAuthorAccountId) → skip, mark processed, do NOT throw")
    void missingPostAuthorOnCommentIsSkippedNotThrown() {
        when(processedEvents.alreadyProcessed("evt-old-1")).thenReturn(false);

        useCase.handle(commentAdded("evt-old-1", "fan-1", null, List.of()));

        verify(repository, never()).save(any());
        verify(processedEvents).markProcessed("evt-old-1", "community.comment.added");
    }

    @Test
    @DisplayName("pre-enrichment comment event still delivers its mention alerts")
    void missingPostAuthorStillFiresMentions() {
        when(processedEvents.alreadyProcessed("evt-old-2")).thenReturn(false);

        useCase.handle(commentAdded("evt-old-2", "fan-1", null, List.of("fan-2")));

        List<Notification> rows = saved();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getType()).isEqualTo(NotificationType.MENTION);
        assertThat(rows.get(0).getAccountId()).isEqualTo("fan-2");
    }

    @Test
    @DisplayName("pre-enrichment reaction event (no postAuthorAccountId) → skip, mark processed, do NOT throw")
    void missingPostAuthorOnReactionIsSkippedNotThrown() {
        when(processedEvents.alreadyProcessed("evt-old-3")).thenReturn(false);

        useCase.handle(reactionAdded("evt-old-3", "fan-1", null));

        verify(repository, never()).save(any());
        verify(processedEvents).markProcessed("evt-old-3", "community.reaction.added");
    }

    /** A counting in-memory channel — never throws (mirrors the v1 logged mocks). */
    private static final class RecordingChannel implements NotificationChannelPort {
        private final String channel;
        private final AtomicInteger count = new AtomicInteger();

        RecordingChannel(String channel) {
            this.channel = channel;
        }

        @Override
        public String channel() {
            return channel;
        }

        @Override
        public DeliveryResult deliver(Notification notification) {
            count.incrementAndGet();
            return new DeliveryResult(true, channel, "ref-" + count.get());
        }
    }
}
