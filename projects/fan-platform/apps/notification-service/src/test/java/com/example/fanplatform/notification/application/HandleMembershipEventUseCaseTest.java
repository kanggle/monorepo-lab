package com.example.fanplatform.notification.application;

import com.example.fanplatform.notification.application.consumer.MembershipEvent;
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
 * TASK-FAN-BE-042 (ADR-MONO-058 § D7): rewritten against the shared
 * {@link EventDedupePort} single-call {@code process(eventId, eventType, work)}
 * shape (was the hand-rolled check-then-act {@code ProcessedEventStore}). Every
 * eventId literal goes through {@link EventIds#uuid} — {@code EventDedupePort} is
 * typed {@code UUID}, so a non-UUID literal like the old {@code "evt-1"} would
 * make {@code UUID.fromString(...)} throw inside {@code useCase.handle(...)}.
 */
class HandleMembershipEventUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-06-11T08:00:00Z");

    private NotificationRepository repository;
    private EventDedupePort dedupePort;
    private RecordingChannel email;
    private RecordingChannel push;
    private HandleMembershipEventUseCase useCase;

    @BeforeEach
    void setUp() {
        repository = mock(NotificationRepository.class);
        dedupePort = mock(EventDedupePort.class);
        email = new RecordingChannel("EMAIL");
        push = new RecordingChannel("PUSH");
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        useCase = new HandleMembershipEventUseCase(
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

    private static MembershipEvent activated(String eventId) {
        return new MembershipEvent(eventId, "fan.membership.activated", "fan-platform", "acc-1",
                "mem-1", "PREMIUM", 1, Instant.parse("2026-06-11T00:00:00Z"),
                Instant.parse("2026-07-11T00:00:00Z"), null, null);
    }

    private static MembershipEvent canceled(String eventId) {
        return new MembershipEvent(eventId, "fan.membership.canceled", "fan-platform", "acc-1",
                "mem-1", "PREMIUM", null, null, null, "user requested",
                Instant.parse("2026-06-11T12:00:00Z"));
    }

    private static MembershipEvent expired(String eventId) {
        return new MembershipEvent(eventId, "fan.membership.expired", "fan-platform", "acc-1",
                "mem-1", "MEMBERS_ONLY", null, null,
                Instant.parse("2026-07-11T00:00:00Z"), null, null);
    }

    @Test
    @DisplayName("activated event → WELCOME notification, dispatched to every channel, marked processed")
    void activatedCreatesWelcome() {
        when(dedupePort.process(any(), any(), any())).thenAnswer(runsWork());

        useCase.handle(activated(EventIds.uuid("evt-1")));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getType()).isEqualTo(NotificationType.WELCOME);
        assertThat(saved.getTitle()).isEqualTo("Welcome to PREMIUM membership");
        assertThat(saved.getStatus()).isEqualTo(NotificationStatus.UNREAD);
        assertThat(saved.getSourceEventId()).isEqualTo(EventIds.uuid("evt-1"));
        assertThat(saved.getAccountId()).isEqualTo("acc-1");
        assertThat(saved.getCreatedAt()).isEqualTo(NOW);
        assertThat(email.count.get()).isEqualTo(1);
        assertThat(push.count.get()).isEqualTo(1);
        verify(dedupePort).process(eq(UUID.fromString(EventIds.uuid("evt-1"))),
                eq("fan.membership.activated"), any());
    }

    @Test
    @DisplayName("canceled event → CANCELLATION notification")
    void canceledCreatesCancellation() {
        when(dedupePort.process(any(), any(), any())).thenAnswer(runsWork());

        useCase.handle(canceled(EventIds.uuid("evt-2")));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(NotificationType.CANCELLATION);
        assertThat(captor.getValue().getTitle()).isEqualTo("Your PREMIUM membership was canceled");
    }

    @Test
    @DisplayName("expired event → EXPIRY_REMINDER notification")
    void expiredCreatesExpiryReminder() {
        when(dedupePort.process(any(), any(), any())).thenAnswer(runsWork());

        useCase.handle(expired(EventIds.uuid("evt-3")));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(NotificationType.EXPIRY_REMINDER);
        assertThat(captor.getValue().getTitle()).isEqualTo("Your MEMBERS_ONLY membership has expired");
        verify(dedupePort).process(eq(UUID.fromString(EventIds.uuid("evt-3"))),
                eq("fan.membership.expired"), any());
    }

    @Test
    @DisplayName("duplicate delivery (already processed) is a no-op — no notification, no dispatch, no re-run")
    void duplicateIsNoOp() {
        when(dedupePort.process(any(), any(), any())).thenReturn(EventDedupePort.Outcome.IGNORED_DUPLICATE);

        useCase.handle(activated(EventIds.uuid("evt-1")));

        verify(repository, never()).save(any());
        assertThat(email.count.get()).isZero();
        assertThat(push.count.get()).isZero();
    }

    @Test
    @DisplayName("TOCTOU regression guard: work's exception propagates out of handle(...) unchanged "
            + "(atomicity relies on the caller's @Transactional rolling back, including the dedupe row)")
    void workExceptionPropagates() {
        when(dedupePort.process(any(), any(), any())).thenAnswer(runsWork());
        NotificationChannelPort throwing = new NotificationChannelPort() {
            @Override
            public String channel() {
                return "BOOM";
            }

            @Override
            public DeliveryResult deliver(Notification notification) {
                throw new IllegalStateException("channel outage");
            }
        };
        HandleMembershipEventUseCase useCaseWithThrowingChannel = new HandleMembershipEventUseCase(
                repository, dedupePort, List.of(throwing), () -> NOW);

        assertThatThrownBy(() -> useCaseWithThrowingChannel.handle(activated(EventIds.uuid("evt-4"))))
                .isInstanceOf(IllegalStateException.class);
        // The notification WAS saved before the throwing channel ran — this test
        // documents that the use case itself does not catch the channel exception;
        // whether the save also rolls back is a real-transaction question the
        // Testcontainers IT (not this mock-based unit test) is authoritative for.
        verify(repository).save(any());
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
