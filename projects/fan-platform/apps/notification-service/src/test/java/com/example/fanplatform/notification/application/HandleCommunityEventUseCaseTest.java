package com.example.fanplatform.notification.application;

import com.example.fanplatform.notification.application.consumer.CommunityEvent;
import com.example.fanplatform.notification.domain.channel.NotificationChannelPort;
import com.example.fanplatform.notification.domain.notification.Notification;
import com.example.fanplatform.notification.domain.notification.NotificationRepository;
import com.example.fanplatform.notification.domain.notification.NotificationStatus;
import com.example.fanplatform.notification.domain.notification.NotificationType;
import com.example.fanplatform.notification.testsupport.EventIds;
import com.example.messaging.dedupe.EventDedupePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
 *
 * <p>TASK-FAN-BE-042 (ADR-MONO-058 § D7): rewritten against the shared
 * {@link EventDedupePort} single-call {@code process(eventId, eventType, work)}
 * shape (was the hand-rolled check-then-act {@code ProcessedEventStore}). Every
 * eventId literal goes through {@link EventIds#uuid} — {@code EventDedupePort} is
 * typed {@code UUID}, so a non-UUID literal like the old {@code "evt-c1"} would
 * make {@code UUID.fromString(...)} throw inside {@code useCase.handle(...)}.
 */
class HandleCommunityEventUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-07-22T09:00:00Z");
    private static final Instant OCCURRED = Instant.parse("2026-07-22T08:59:00Z");
    private static final String TENANT = "fan-platform";

    private NotificationRepository repository;
    private EventDedupePort dedupePort;
    private RecordingChannel email;
    private RecordingChannel push;
    private HandleCommunityEventUseCase useCase;

    @BeforeEach
    void setUp() {
        repository = mock(NotificationRepository.class);
        dedupePort = mock(EventDedupePort.class);
        email = new RecordingChannel("EMAIL");
        push = new RecordingChannel("PUSH");
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        useCase = new HandleCommunityEventUseCase(
                repository, dedupePort, List.of(email, push), () -> NOW);
    }

    /** Stubs {@code process(...)} to run the supplied work and report APPLIED — first delivery. */
    private static Answer<EventDedupePort.Outcome> runsWork() {
        return invocation -> {
            Runnable work = invocation.getArgument(2);
            work.run();
            return EventDedupePort.Outcome.APPLIED;
        };
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
        when(dedupePort.process(any(), any(), any())).thenAnswer(runsWork());

        useCase.handle(commentAdded(EventIds.uuid("evt-c1"), "fan-1", "author-1", List.of()));

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
        assertThat(reply.getSourceEventId()).isEqualTo(EventIds.uuid("evt-c1"));
        assertThat(reply.getSourceEventType()).isEqualTo("community.comment.added");
        assertThat(reply.getCreatedAt()).isEqualTo(NOW);
        assertThat(email.count.get()).isEqualTo(1);
        assertThat(push.count.get()).isEqualTo(1);
        verify(dedupePort).process(eq(UUID.fromString(EventIds.uuid("evt-c1"))),
                eq("community.comment.added"), any());
    }

    @Test
    @DisplayName("comment.added with mentions → one MENTION per mentioned account, plus the REPLY")
    void commentCreatesMentionPerAccount() {
        when(dedupePort.process(any(), any(), any())).thenAnswer(runsWork());

        useCase.handle(commentAdded(EventIds.uuid("evt-c2"), "fan-1", "author-1",
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
        assertThat(rows).allSatisfy(n -> assertThat(n.getSourceEventId()).isEqualTo(EventIds.uuid("evt-c2")));
        assertThat(email.count.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("duplicate mentioned accounts are deduped (one MENTION row per account)")
    void duplicateMentionsAreDeduped() {
        when(dedupePort.process(any(), any(), any())).thenAnswer(runsWork());

        useCase.handle(commentAdded(EventIds.uuid("evt-c3"), "fan-1", "author-1",
                List.of("fan-2", "fan-2")));

        List<Notification> rows = saved();
        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(Notification::getAccountId)
                .containsExactly("author-1", "fan-2");
    }

    @Test
    @DisplayName("empty mentionedAccountIds → no mention alert, not an error")
    void emptyMentionListIsNotAnError() {
        when(dedupePort.process(any(), any(), any())).thenAnswer(runsWork());

        useCase.handle(commentAdded(EventIds.uuid("evt-c4"), "fan-1", "author-1", List.of()));

        assertThat(saved()).hasSize(1);
    }

    @Test
    @DisplayName("AC-3: commenter IS the post author → no REPLY, still marked processed")
    void selfReplyIsSuppressed() {
        when(dedupePort.process(any(), any(), any())).thenAnswer(runsWork());

        useCase.handle(commentAdded(EventIds.uuid("evt-c5"), "author-1", "author-1", List.of()));

        verify(repository, never()).save(any());
        assertThat(email.count.get()).isZero();
        assertThat(push.count.get()).isZero();
        // "Still marked processed" now means the dedupe row is committed by
        // process(...) regardless of whether work created any rows — verified by
        // the fact process(...) was invoked with this eventId (below) rather than
        // by a separate markProcessed call, which no longer exists.
        verify(dedupePort).process(eq(UUID.fromString(EventIds.uuid("evt-c5"))),
                eq("community.comment.added"), any());
    }

    @Test
    @DisplayName("AC-3 spirit: a self-mention is suppressed, other mentions still fire")
    void selfMentionIsSuppressed() {
        when(dedupePort.process(any(), any(), any())).thenAnswer(runsWork());

        useCase.handle(commentAdded(EventIds.uuid("evt-c6"), "fan-1", "author-1",
                List.of("fan-1", "fan-2")));

        List<Notification> rows = saved();
        assertThat(rows).extracting(Notification::getAccountId)
                .containsExactly("author-1", "fan-2");
    }

    @Test
    @DisplayName("reaction.added → REACTION_BADGE to the post author with the reaction type")
    void reactionCreatesBadge() {
        when(dedupePort.process(any(), any(), any())).thenAnswer(runsWork());

        useCase.handle(reactionAdded(EventIds.uuid("evt-r1"), "fan-1", "author-1"));

        List<Notification> rows = saved();
        assertThat(rows).hasSize(1);
        Notification badge = rows.get(0);
        assertThat(badge.getType()).isEqualTo(NotificationType.REACTION_BADGE);
        assertThat(badge.getAccountId()).isEqualTo("author-1");
        assertThat(badge.getTitle()).isEqualTo("Someone reacted to your post");
        assertThat(badge.getBody()).contains("LIKE").contains("fan-1");
        assertThat(badge.getPostId()).isEqualTo("post-1");
        assertThat(badge.getMembershipId()).isNull();
    }

    @Test
    @DisplayName("AC-3: reactor IS the post author → no REACTION_BADGE, still marked processed")
    void selfReactionIsSuppressed() {
        when(dedupePort.process(any(), any(), any())).thenAnswer(runsWork());

        useCase.handle(reactionAdded(EventIds.uuid("evt-r2"), "author-1", "author-1"));

        verify(repository, never()).save(any());
        assertThat(push.count.get()).isZero();
        verify(dedupePort).process(eq(UUID.fromString(EventIds.uuid("evt-r2"))),
                eq("community.reaction.added"), any());
    }

    @Test
    @DisplayName("AC-4: duplicate delivery (already processed) is a no-op — no row, no dispatch, no re-run")
    void duplicateIsNoOp() {
        when(dedupePort.process(any(), any(), any())).thenReturn(EventDedupePort.Outcome.IGNORED_DUPLICATE);

        useCase.handle(commentAdded(EventIds.uuid("evt-c1"), "fan-1", "author-1", List.of("fan-2")));

        verify(repository, never()).save(any());
        assertThat(email.count.get()).isZero();
        assertThat(push.count.get()).isZero();
    }

    @Test
    @DisplayName("pre-enrichment comment event (no postAuthorAccountId) → skip, mark processed, do NOT throw")
    void missingPostAuthorOnCommentIsSkippedNotThrown() {
        when(dedupePort.process(any(), any(), any())).thenAnswer(runsWork());

        useCase.handle(commentAdded(EventIds.uuid("evt-old-1"), "fan-1", null, List.of()));

        verify(repository, never()).save(any());
        verify(dedupePort).process(eq(UUID.fromString(EventIds.uuid("evt-old-1"))),
                eq("community.comment.added"), any());
    }

    @Test
    @DisplayName("pre-enrichment comment event still delivers its mention alerts")
    void missingPostAuthorStillFiresMentions() {
        when(dedupePort.process(any(), any(), any())).thenAnswer(runsWork());

        useCase.handle(commentAdded(EventIds.uuid("evt-old-2"), "fan-1", null, List.of("fan-2")));

        List<Notification> rows = saved();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getType()).isEqualTo(NotificationType.MENTION);
        assertThat(rows.get(0).getAccountId()).isEqualTo("fan-2");
    }

    @Test
    @DisplayName("pre-enrichment reaction event (no postAuthorAccountId) → skip, mark processed, do NOT throw")
    void missingPostAuthorOnReactionIsSkippedNotThrown() {
        when(dedupePort.process(any(), any(), any())).thenAnswer(runsWork());

        useCase.handle(reactionAdded(EventIds.uuid("evt-old-3"), "fan-1", null));

        verify(repository, never()).save(any());
        verify(dedupePort).process(eq(UUID.fromString(EventIds.uuid("evt-old-3"))),
                eq("community.reaction.added"), any());
    }

    @Test
    @DisplayName("TOCTOU regression guard: work's exception propagates out of handle(...) unchanged "
            + "(atomicity relies on the caller's @Transactional rolling back, including the dedupe row)")
    void workExceptionPropagates() {
        RuntimeException boom = new RuntimeException("downstream failure");
        when(dedupePort.process(any(), any(), any())).thenAnswer(invocation -> {
            Runnable work = invocation.getArgument(2);
            work.run();
            return EventDedupePort.Outcome.APPLIED;
        });
        // An unsupported event type is the one path inside processNew(...) that
        // throws — reuse it to prove the exception is not swallowed by handle(...).
        CommunityEvent unsupported = new CommunityEvent(EventIds.uuid("evt-bad"), "community.unknown",
                TENANT, "post-1", null, "fan-1", "author-1", List.of(), null, OCCURRED);

        assertThatThrownBy(() -> useCase.handle(unsupported))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).save(any());
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
