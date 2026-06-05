package com.example.erp.notification.application;

import com.example.erp.notification.application.port.outbound.NotificationMetricsPort;
import com.example.erp.notification.application.query.InboxPage;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class QueryInboxUseCaseTest {

    @Mock NotificationRepository repository;
    @Mock NotificationMetricsPort metrics;
    @InjectMocks QueryInboxUseCase useCase;

    private Notification notif(String id, String recipient) {
        return Notification.create(id, "erp", recipient, NotificationType.APPROVAL_SUBMITTED,
                "t", "b", SourceRef.approval("appr-1"), Instant.parse("2026-06-05T10:00:00Z"));
    }

    @Test
    void listIsRecipientScopedAndPaged() {
        when(repository.findInbox("erp", "emp-1", null, 0, 20))
                .thenReturn(List.of(notif("ntf-1", "emp-1")));
        when(repository.countInbox("erp", "emp-1", null)).thenReturn(1L);

        InboxPage page = useCase.list("erp", "emp-1", null, 0, 20);
        assertThat(page.content()).hasSize(1);
        assertThat(page.totalElements()).isEqualTo(1L);
    }

    @Test
    void getOneOwnedReturnsNotification() {
        when(repository.findByIdForRecipient("erp", "ntf-1", "emp-1"))
                .thenReturn(Optional.of(notif("ntf-1", "emp-1")));
        assertThat(useCase.getOne("erp", "emp-1", "ntf-1").id()).isEqualTo("ntf-1");
    }

    @Test
    void getOneForeignRecipientIsNotFound() {
        when(repository.findByIdForRecipient("erp", "ntf-9", "emp-1"))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.getOne("erp", "emp-1", "ntf-9"))
                .isInstanceOf(NotificationNotFoundException.class);
    }
}
