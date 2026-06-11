package com.example.fanplatform.notification.application;

import com.example.fanplatform.notification.application.consumer.MembershipEvent;
import com.example.fanplatform.notification.application.port.ProcessedEventStore;
import com.example.fanplatform.notification.domain.channel.NotificationChannelPort;
import com.example.fanplatform.notification.domain.notification.Notification;
import com.example.fanplatform.notification.domain.notification.NotificationRepository;
import com.example.fanplatform.notification.domain.notification.NotificationStatus;
import com.example.fanplatform.notification.domain.notification.NotificationType;
import com.example.fanplatform.notification.domain.time.ClockPort;
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

class HandleMembershipEventUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-06-11T08:00:00Z");

    private NotificationRepository repository;
    private ProcessedEventStore processedEvents;
    private RecordingChannel email;
    private RecordingChannel push;
    private HandleMembershipEventUseCase useCase;

    @BeforeEach
    void setUp() {
        repository = mock(NotificationRepository.class);
        processedEvents = mock(ProcessedEventStore.class);
        email = new RecordingChannel("EMAIL");
        push = new RecordingChannel("PUSH");
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        useCase = new HandleMembershipEventUseCase(
                repository, processedEvents, List.of(email, push), () -> NOW);
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
        when(processedEvents.alreadyProcessed("evt-1")).thenReturn(false);

        useCase.handle(activated("evt-1"));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getType()).isEqualTo(NotificationType.WELCOME);
        assertThat(saved.getTitle()).isEqualTo("Welcome to PREMIUM membership");
        assertThat(saved.getStatus()).isEqualTo(NotificationStatus.UNREAD);
        assertThat(saved.getSourceEventId()).isEqualTo("evt-1");
        assertThat(saved.getAccountId()).isEqualTo("acc-1");
        assertThat(saved.getCreatedAt()).isEqualTo(NOW);
        assertThat(email.count.get()).isEqualTo(1);
        assertThat(push.count.get()).isEqualTo(1);
        verify(processedEvents).markProcessed("evt-1", "fan.membership.activated");
    }

    @Test
    @DisplayName("canceled event → CANCELLATION notification")
    void canceledCreatesCancellation() {
        when(processedEvents.alreadyProcessed("evt-2")).thenReturn(false);

        useCase.handle(canceled("evt-2"));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(NotificationType.CANCELLATION);
        assertThat(captor.getValue().getTitle()).isEqualTo("Your PREMIUM membership was canceled");
    }

    @Test
    @DisplayName("expired event → EXPIRY_REMINDER notification")
    void expiredCreatesExpiryReminder() {
        when(processedEvents.alreadyProcessed("evt-3")).thenReturn(false);

        useCase.handle(expired("evt-3"));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(NotificationType.EXPIRY_REMINDER);
        assertThat(captor.getValue().getTitle()).isEqualTo("Your MEMBERS_ONLY membership has expired");
        verify(processedEvents).markProcessed("evt-3", "fan.membership.expired");
    }

    @Test
    @DisplayName("duplicate delivery (already processed) is a no-op — no notification, no dispatch, no re-mark")
    void duplicateIsNoOp() {
        when(processedEvents.alreadyProcessed("evt-1")).thenReturn(true);

        useCase.handle(activated("evt-1"));

        verify(repository, never()).save(any());
        assertThat(email.count.get()).isZero();
        assertThat(push.count.get()).isZero();
        verify(processedEvents, never()).markProcessed(eq("evt-1"), any());
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
