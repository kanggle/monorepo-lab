package com.example.erp.notification.application;

import com.example.common.page.PageResult;
import com.example.erp.notification.application.port.outbound.CallerEmployeePort;
import com.example.erp.notification.application.port.outbound.CallerEmployeePort.CallerEmployee;
import com.example.erp.notification.application.port.outbound.NotificationMetricsPort;
import com.example.erp.notification.domain.error.RecipientResolveUnavailableException;
import com.example.erp.notification.domain.error.NotificationNotFoundException;
import com.example.erp.notification.domain.notification.Notification;
import com.example.erp.notification.domain.notification.NotificationType;
import com.example.erp.notification.domain.notification.SourceRef;
import com.example.erp.notification.domain.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class QueryInboxUseCaseTest {

    @Mock NotificationRepository repository;
    @Mock NotificationMetricsPort metrics;
    @Mock CallerEmployeePort callerEmployee;
    @InjectMocks QueryInboxUseCase useCase;

    /** TASK-MONO-776: the caller is account "acc-1", linked to employee "emp-1" (the recipient). */
    @BeforeEach
    void linked() {
        lenient().when(callerEmployee.resolve("acc-1", "erp")).thenReturn(CallerEmployee.linked("emp-1"));
    }

    private Notification notif(String id, String recipient) {
        return Notification.create(id, "erp", recipient, NotificationType.APPROVAL_SUBMITTED,
                "t", "b", SourceRef.approval("appr-1"), Instant.parse("2026-06-05T10:00:00Z"));
    }

    @Test
    void listIsRecipientScopedAndPaged() {
        when(repository.findInbox("erp", "emp-1", null, 0, 20))
                .thenReturn(List.of(notif("ntf-1", "emp-1")));
        when(repository.countInbox("erp", "emp-1", null)).thenReturn(1L);

        PageResult<com.example.erp.notification.domain.notification.Notification> page =
                useCase.list("erp", "acc-1", null, 0, 20);
        assertThat(page.content()).hasSize(1);
        assertThat(page.totalElements()).isEqualTo(1L);
        // ADR-MONO-058 § D3 — AC-2: 1 element / size 20 → 1 page (ceiling division).
        assertThat(page.totalPages()).isEqualTo(1);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("AC-2: totalPages is a ceiling division over a non-exact multiple")
    void totalPagesIsCeilingDivisionForNonExactMultiple() {
        when(repository.findInbox("erp", "emp-1", null, 0, 10))
                .thenReturn(List.of(notif("ntf-1", "emp-1")));
        when(repository.countInbox("erp", "emp-1", null)).thenReturn(25L);

        PageResult<com.example.erp.notification.domain.notification.Notification> page =
                useCase.list("erp", "acc-1", null, 0, 10);
        // 25 elements / 10 per page → 3 pages (not 2, not 2.5 truncated).
        assertThat(page.totalPages()).isEqualTo(3);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("AC-2 edge case: zero elements -> totalPages 0, not 1")
    void totalPagesIsZeroForEmptyResult() {
        when(repository.findInbox("erp", "emp-1", null, 0, 20)).thenReturn(List.of());
        when(repository.countInbox("erp", "emp-1", null)).thenReturn(0L);

        PageResult<com.example.erp.notification.domain.notification.Notification> page =
                useCase.list("erp", "acc-1", null, 0, 20);
        assertThat(page.totalElements()).isEqualTo(0L);
        assertThat(page.totalPages()).isEqualTo(0);
    }

    @Test
    void getOneOwnedReturnsNotification() {
        when(repository.findByIdForRecipient("erp", "ntf-1", "emp-1"))
                .thenReturn(Optional.of(notif("ntf-1", "emp-1")));
        assertThat(useCase.getOne("erp", "acc-1", "ntf-1").id()).isEqualTo("ntf-1");
    }

    @Test
    void getOneForeignRecipientIsNotFound() {
        when(repository.findByIdForRecipient("erp", "ntf-9", "emp-1"))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.getOne("erp", "acc-1", "ntf-9"))
                .isInstanceOf(NotificationNotFoundException.class);
    }

    // ---- TASK-MONO-776 (notification-api.md § v1.1) ----

    @Test
    @org.junit.jupiter.api.DisplayName("776 AC-6: recipient = the caller's EMPLOYEE — the sub is never the predicate")
    void predicateIsTheLinkedEmployeeNotTheSub() {
        when(repository.findInbox("erp", "emp-1", null, 0, 20))
                .thenReturn(List.of(notif("ntf-1", "emp-1")));
        when(repository.countInbox("erp", "emp-1", null)).thenReturn(1L);

        assertThat(useCase.list("erp", "acc-1", null, 0, 20).totalElements()).isEqualTo(1L);
        verify(repository, never()).findInbox(org.mockito.ArgumentMatchers.eq("erp"),
                org.mockito.ArgumentMatchers.eq("acc-1"), any(), anyInt(), anyInt());
    }

    @Test
    @org.junit.jupiter.api.DisplayName("776 AC-6: another account (linked to emp-2) gets 404 for emp-1's notification")
    void otherAccountCannotReadIt() {
        when(callerEmployee.resolve("acc-2", "erp")).thenReturn(CallerEmployee.linked("emp-2"));
        when(repository.findByIdForRecipient("erp", "ntf-1", "emp-2")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.getOne("erp", "acc-2", "ntf-1"))
                .isInstanceOf(NotificationNotFoundException.class);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("776: unlinked caller → empty inbox (no query), detail 404")
    void unlinkedCaller() {
        when(callerEmployee.resolve("acc-x", "erp")).thenReturn(CallerEmployee.notLinked());
        var page = useCase.list("erp", "acc-x", null, 0, 20);
        assertThat(page.totalElements()).isZero();
        assertThat(page.totalPages()).isZero();
        verify(repository, never()).findInbox(any(), any(), any(), anyInt(), anyInt());
        assertThatThrownBy(() -> useCase.getOne("erp", "acc-x", "ntf-1"))
                .isInstanceOf(NotificationNotFoundException.class);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("776: masterdata unavailable → 503 exception, NOT an empty inbox")
    void unavailableIsNotEmpty() {
        when(callerEmployee.resolve("acc-1", "erp")).thenReturn(CallerEmployee.unavailable());
        assertThatThrownBy(() -> useCase.list("erp", "acc-1", null, 0, 20))
                .isInstanceOf(RecipientResolveUnavailableException.class);
    }
}
