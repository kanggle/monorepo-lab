package com.example.erp.notification.application;

import com.example.erp.notification.application.port.outbound.ClockPort;
import com.example.erp.notification.application.port.outbound.NotificationMetricsPort;
import com.example.erp.notification.domain.error.NotificationNotFoundException;
import com.example.erp.notification.domain.notification.Notification;
import com.example.erp.notification.domain.notification.NotificationType;
import com.example.erp.notification.domain.notification.SourceRef;
import com.example.erp.notification.domain.notification.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class MarkNotificationReadUseCaseTest {

    @Mock NotificationRepository repository;
    @Mock ClockPort clock;
    @Mock NotificationMetricsPort metrics;
    @InjectMocks MarkNotificationReadUseCase useCase;

    private final Instant created = Instant.parse("2026-06-05T10:00:00Z");
    private final Instant markAt = Instant.parse("2026-06-05T11:00:00Z");

    private Notification fresh() {
        return Notification.create("ntf-1", "erp", "emp-1", NotificationType.APPROVAL_SUBMITTED,
                "t", "b", SourceRef.approval("appr-1"), created);
    }

    @Test
    void firstMarkSetsReadAndPersists() {
        Notification n = fresh();
        when(repository.findByIdForRecipient("erp", "ntf-1", "emp-1")).thenReturn(Optional.of(n));
        when(clock.now()).thenReturn(markAt);

        Notification result = useCase.markRead("erp", "emp-1", "ntf-1");
        assertThat(result.read()).isTrue();
        assertThat(result.readAt()).contains(markAt);
        verify(repository, times(1)).save(n);
    }

    @Test
    void reMarkIsIdempotentNoOpAndDoesNotPersist() {
        Notification n = fresh();
        n.markRead(markAt); // already read
        when(repository.findByIdForRecipient("erp", "ntf-1", "emp-1")).thenReturn(Optional.of(n));

        Notification result = useCase.markRead("erp", "emp-1", "ntf-1");
        assertThat(result.readAt()).contains(markAt);
        // Already-read → no second persist (clock not consulted, readAt preserved).
        verify(repository, never()).save(n);
    }

    @Test
    void foreignRecipientIsNotFound() {
        when(repository.findByIdForRecipient("erp", "ntf-9", "emp-1")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.markRead("erp", "emp-1", "ntf-9"))
                .isInstanceOf(NotificationNotFoundException.class);
    }
}
