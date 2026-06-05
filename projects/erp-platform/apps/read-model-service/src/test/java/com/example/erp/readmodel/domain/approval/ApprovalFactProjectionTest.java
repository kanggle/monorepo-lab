package com.example.erp.readmodel.domain.approval;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link ApprovalFactProjection} terminal-once + out-of-order
 * transition rules (the correctness core of TASK-ERP-BE-010). Pure domain — no
 * Spring / Mockito.
 */
class ApprovalFactProjectionTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant T1 = Instant.parse("2026-01-02T00:00:00Z");
    private static final Instant T2 = Instant.parse("2026-01-03T00:00:00Z");

    @Test
    void submittedThenApprovedReachesTerminalWithBothTimestamps() {
        ApprovalFactProjection fact = ApprovalFactProjection.ofSubmitted(
                "appr-1", ApprovalSubjectType.DEPARTMENT, "dept-1", "emp-appr", "emp-sub",
                T0, T0, "evt-1");

        fact.applyTerminal(ApprovalStatus.APPROVED, ApprovalSubjectType.DEPARTMENT, "dept-1",
                "emp-appr", "emp-sub", T1, null, T1, "evt-2");

        assertThat(fact.status()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(fact.submittedAt()).isEqualTo(T0);
        assertThat(fact.finalizedAt()).isEqualTo(T1);
        assertThat(fact.lastReason()).isNull();
        assertThat(fact.isTerminal()).isTrue();
    }

    @Test
    void terminalOnce_laterSubmittedDoesNotRevert() {
        ApprovalFactProjection fact = ApprovalFactProjection.ofTerminal(
                "appr-1", ApprovalStatus.APPROVED, ApprovalSubjectType.DEPARTMENT, "dept-1",
                "emp-appr", "emp-sub", T1, null, T1, "evt-2");

        // A late / out-of-contract SUBMITTED must NOT revert the terminal.
        fact.applySubmitted(ApprovalSubjectType.DEPARTMENT, "dept-1", "emp-appr", "emp-sub",
                T0, T2, "evt-3");

        assertThat(fact.status()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(fact.isTerminal()).isTrue();
    }

    @Test
    void outOfOrder_terminalBeforeSubmittedLeavesSubmittedAtAbsent() {
        // Terminal arrives first (replay-from-middle): row created, submittedAt ABSENT.
        ApprovalFactProjection fact = ApprovalFactProjection.ofTerminal(
                "appr-1", ApprovalStatus.REJECTED, ApprovalSubjectType.EMPLOYEE, "emp-1",
                "emp-appr", "emp-sub", T1, "예산 근거 부족", T1, "evt-2");

        assertThat(fact.status()).isEqualTo(ApprovalStatus.REJECTED);
        assertThat(fact.submittedAt()).isNull();
        assertThat(fact.finalizedAt()).isEqualTo(T1);
        assertThat(fact.lastReason()).isEqualTo("예산 근거 부족");
    }

    @Test
    void lastTerminalWins_secondTerminalDoesNotUnfinalize() {
        ApprovalFactProjection fact = ApprovalFactProjection.ofTerminal(
                "appr-1", ApprovalStatus.APPROVED, ApprovalSubjectType.DEPARTMENT, "dept-1",
                "emp-appr", "emp-sub", T1, null, T1, "evt-2");

        // Out-of-contract second terminal — stays terminal (never SUBMITTED).
        fact.applyTerminal(ApprovalStatus.WITHDRAWN, ApprovalSubjectType.DEPARTMENT, "dept-1",
                "emp-appr", "emp-sub", T2, "기안 수정", T2, "evt-3");

        assertThat(fact.status()).isEqualTo(ApprovalStatus.WITHDRAWN);
        assertThat(fact.isTerminal()).isTrue();
        assertThat(fact.lastReason()).isEqualTo("기안 수정");
    }

    @Test
    void submittedUpsertOnExistingNonTerminalSetsSubmittedAtOnce() {
        ApprovalFactProjection fact = ApprovalFactProjection.ofSubmitted(
                "appr-1", ApprovalSubjectType.DEPARTMENT, "dept-1", "emp-appr", "emp-sub",
                T0, T0, "evt-1");

        // A re-submitted (duplicate-by-content, different eventId) keeps the first submittedAt.
        fact.applySubmitted(ApprovalSubjectType.DEPARTMENT, "dept-1", "emp-appr", "emp-sub",
                T2, T2, "evt-9");

        assertThat(fact.status()).isEqualTo(ApprovalStatus.SUBMITTED);
        assertThat(fact.submittedAt()).isEqualTo(T0);
    }
}
