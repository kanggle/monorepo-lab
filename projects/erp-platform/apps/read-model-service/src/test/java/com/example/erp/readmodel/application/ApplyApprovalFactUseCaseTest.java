package com.example.erp.readmodel.application;

import com.example.erp.readmodel.application.command.ApprovalFactCommand;
import com.example.erp.readmodel.domain.approval.ApprovalFactProjection;
import com.example.erp.readmodel.domain.approval.ApprovalStatus;
import com.example.erp.readmodel.domain.approval.ApprovalSubjectType;
import com.example.erp.readmodel.domain.approval.repository.ApprovalFactProjectionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ApplyApprovalFactUseCase}: the 4-event latest-state
 * upsert, terminal-once, out-of-order (terminal-before-submitted), and dedupe
 * (T8). {@code @ExtendWith(MockitoExtension)} STRICT_STUBS.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class ApplyApprovalFactUseCaseTest {

    @Mock ApprovalFactProjectionRepository approvalRepository;
    @Mock EventDedupeService dedupeService;

    @InjectMocks ApplyApprovalFactUseCase useCase;

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant T1 = Instant.parse("2026-01-02T00:00:00Z");

    private ApprovalFactCommand submitted(String eventId) {
        return new ApprovalFactCommand(eventId, "erp.approval.submitted.v1", "appr-1",
                ApprovalStatus.SUBMITTED, ApprovalSubjectType.DEPARTMENT, "dept-1",
                "emp-appr", "emp-sub", T0, T0, null, null);
    }

    private ApprovalFactCommand approved(String eventId) {
        return new ApprovalFactCommand(eventId, "erp.approval.approved.v1", "appr-1",
                ApprovalStatus.APPROVED, ApprovalSubjectType.DEPARTMENT, "dept-1",
                "emp-appr", "emp-sub", T1, null, T1, null);
    }

    private ApprovalFactCommand rejected(String eventId) {
        return new ApprovalFactCommand(eventId, "erp.approval.rejected.v1", "appr-1",
                ApprovalStatus.REJECTED, ApprovalSubjectType.EMPLOYEE, "emp-1",
                "emp-appr", "emp-sub", T1, null, T1, "예산 근거 부족");
    }

    @Test
    void submittedInsertsNewFactAndMarksProcessed() {
        when(dedupeService.isDuplicate("evt-1")).thenReturn(false);
        when(approvalRepository.findById("appr-1")).thenReturn(Optional.empty());

        useCase.apply(submitted("evt-1"));

        ArgumentCaptor<ApprovalFactProjection> captor =
                ArgumentCaptor.forClass(ApprovalFactProjection.class);
        verify(approvalRepository).save(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(ApprovalStatus.SUBMITTED);
        assertThat(captor.getValue().submittedAt()).isEqualTo(T0);
        assertThat(captor.getValue().finalizedAt()).isNull();
        verify(dedupeService).markProcessed("evt-1", "erp.approval.submitted.v1", "appr-1");
    }

    @Test
    void approvedOnExistingSubmittedTransitionsToTerminal() {
        ApprovalFactProjection existing = ApprovalFactProjection.ofSubmitted(
                "appr-1", ApprovalSubjectType.DEPARTMENT, "dept-1", "emp-appr", "emp-sub",
                T0, T0, "evt-1");
        when(dedupeService.isDuplicate("evt-2")).thenReturn(false);
        when(approvalRepository.findById("appr-1")).thenReturn(Optional.of(existing));

        useCase.apply(approved("evt-2"));

        assertThat(existing.status()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(existing.submittedAt()).isEqualTo(T0);
        assertThat(existing.finalizedAt()).isEqualTo(T1);
        verify(approvalRepository).save(existing);
        verify(dedupeService).markProcessed("evt-2", "erp.approval.approved.v1", "appr-1");
    }

    @Test
    void rejectedRequiresReasonAndSetsFinalizedAt() {
        when(dedupeService.isDuplicate("evt-r")).thenReturn(false);
        when(approvalRepository.findById("appr-1")).thenReturn(Optional.empty());

        useCase.apply(rejected("evt-r"));

        ArgumentCaptor<ApprovalFactProjection> captor =
                ArgumentCaptor.forClass(ApprovalFactProjection.class);
        verify(approvalRepository).save(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(ApprovalStatus.REJECTED);
        assertThat(captor.getValue().lastReason()).isEqualTo("예산 근거 부족");
        // Out-of-order: terminal with no prior submitted → submittedAt ABSENT.
        assertThat(captor.getValue().submittedAt()).isNull();
    }

    @Test
    void terminalOnce_submittedAfterTerminalDoesNotRevert() {
        ApprovalFactProjection terminal = ApprovalFactProjection.ofTerminal(
                "appr-1", ApprovalStatus.APPROVED, ApprovalSubjectType.DEPARTMENT, "dept-1",
                "emp-appr", "emp-sub", T1, null, T1, "evt-2");
        when(dedupeService.isDuplicate("evt-late")).thenReturn(false);
        when(approvalRepository.findById("appr-1")).thenReturn(Optional.of(terminal));

        useCase.apply(submitted("evt-late"));

        assertThat(terminal.status()).isEqualTo(ApprovalStatus.APPROVED);
        verify(approvalRepository).save(terminal);
    }

    @Test
    void duplicateEventIsSkippedWithoutMutation() {
        when(dedupeService.isDuplicate("evt-1")).thenReturn(true);

        useCase.apply(submitted("evt-1"));

        verify(approvalRepository, never()).save(any());
        verify(dedupeService, never()).markProcessed(anyString(), anyString(), anyString());
    }
}
