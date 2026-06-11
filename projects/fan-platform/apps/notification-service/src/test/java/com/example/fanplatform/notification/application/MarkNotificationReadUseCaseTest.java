package com.example.fanplatform.notification.application;

import com.example.fanplatform.notification.domain.notification.Notification;
import com.example.fanplatform.notification.domain.notification.NotificationNotFoundException;
import com.example.fanplatform.notification.domain.notification.NotificationRepository;
import com.example.fanplatform.notification.domain.notification.NotificationStatus;
import com.example.fanplatform.notification.domain.notification.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarkNotificationReadUseCaseTest {

    private static final Instant CREATED = Instant.parse("2026-06-11T08:00:00Z");
    private static final Instant MARK_NOW = Instant.parse("2026-06-11T09:00:00Z");
    private static final ActorContext ACTOR = new ActorContext("acc-1", "fan-platform", Set.of("FAN"));

    private NotificationRepository repository;
    private MarkNotificationReadUseCase useCase;

    @BeforeEach
    void setUp() {
        repository = mock(NotificationRepository.class);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        useCase = new MarkNotificationReadUseCase(repository, () -> MARK_NOW);
    }

    private static Notification unread(String id) {
        return Notification.create(id, "fan-platform", "acc-1", NotificationType.WELCOME,
                "t", "b", "evt-" + id, "fan.membership.activated", "mem-1", CREATED);
    }

    @Test
    @DisplayName("UNREAD → READ sets readAt and persists")
    void marksUnreadAsRead() {
        Notification n = unread("n1");
        when(repository.findByIdScoped("n1", "fan-platform", "acc-1")).thenReturn(Optional.of(n));

        Notification result = useCase.markRead(ACTOR, "n1");

        assertThat(result.getStatus()).isEqualTo(NotificationStatus.READ);
        assertThat(result.getReadAt()).isEqualTo(MARK_NOW);
        verify(repository).save(n);
    }

    @Test
    @DisplayName("re-marking an already-READ notification is an idempotent no-op (preserves readAt, no save)")
    void reMarkIsIdempotent() {
        Notification n = unread("n1");
        Instant firstReadAt = Instant.parse("2026-06-11T08:30:00Z");
        n.markRead(firstReadAt);
        when(repository.findByIdScoped("n1", "fan-platform", "acc-1")).thenReturn(Optional.of(n));

        Notification result = useCase.markRead(ACTOR, "n1");

        assertThat(result.getStatus()).isEqualTo(NotificationStatus.READ);
        assertThat(result.getReadAt()).isEqualTo(firstReadAt);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("cross-account / unknown id → NotificationNotFoundException (no leak)")
    void notFoundForForeignId() {
        when(repository.findByIdScoped("nX", "fan-platform", "acc-1")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.markRead(ACTOR, "nX"))
                .isInstanceOf(NotificationNotFoundException.class);
    }
}
