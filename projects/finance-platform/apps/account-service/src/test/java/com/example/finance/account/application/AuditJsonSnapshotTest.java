package com.example.finance.account.application;

import com.example.finance.account.application.command.OpenAccountCommand;
import com.example.finance.account.application.event.AccountEventPublisher;
import com.example.finance.account.application.port.outbound.ClockPort;
import com.example.finance.account.application.port.outbound.CompliancePort;
import com.example.finance.account.domain.account.Account;
import com.example.finance.account.domain.account.repository.AccountRepository;
import com.example.finance.account.domain.account.status.AccountStatusHistoryRepository;
import com.example.finance.account.domain.audit.AuditLog;
import com.example.finance.account.domain.audit.AuditLogRepository;
import com.example.finance.account.domain.balance.Balance;
import com.example.finance.account.domain.balance.repository.BalanceRepository;
import com.example.finance.account.domain.transaction.repository.TransactionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

/**
 * Byte-equal snapshot test for audit JSON payloads (audit-heavy A1/A3, F6).
 * Verifies that the Jackson {@link ObjectMapper} + {@link java.util.LinkedHashMap}
 * path produces deterministic JSON with the correct key order, so the
 * {@code before_state} / {@code after_state} columns in {@code audit_log} are
 * contract-stable across refactors.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuditJsonSnapshotTest {

    private static final String TENANT = "finance";
    private static final Instant NOW = Instant.parse("2026-05-25T00:00:00Z");
    private static final ActorContext HOLDER =
            new ActorContext("user-1", TENANT, Set.of());

    @Mock AccountRepository accountRepository;
    @Mock BalanceRepository balanceRepository;
    @Mock TransactionRepository transactionRepository;
    @Mock AccountStatusHistoryRepository historyRepository;
    @Mock AuditLogRepository auditLogRepository;
    @Mock CompliancePort compliancePort;
    @Mock ComplianceFailureRecorder complianceFailureRecorder;
    @Mock ClockPort clock;
    @Mock AccountEventPublisher eventPublisher;

    @Test
    @DisplayName("audit-heavy A3: openAccount after_state JSON is byte-equal across calls")
    void openAccountAuditJsonByteEqual() {
        // Use a REAL ObjectMapper (not a mock) to verify byte-equal JSON output.
        ObjectMapper realMapper = new ObjectMapper();
        AccountApplicationService svc = new AccountApplicationService(
                accountRepository, balanceRepository, transactionRepository,
                historyRepository, auditLogRepository, compliancePort,
                complianceFailureRecorder, clock, eventPublisher, realMapper);

        lenient().when(clock.now()).thenReturn(NOW);
        lenient().when(accountRepository.save(any(Account.class)))
                .thenAnswer(i -> i.getArgument(0));
        lenient().when(balanceRepository.save(any(Balance.class)))
                .thenAnswer(i -> i.getArgument(0));
        lenient().when(auditLogRepository.save(any(AuditLog.class)))
                .thenAnswer(i -> i.getArgument(0));

        // Call openAccount twice — the after_state JSON must be byte-equal.
        svc.openAccount(new OpenAccountCommand(HOLDER, "cust-1", "KRW", "NONE"));
        svc.openAccount(new OpenAccountCommand(HOLDER, "cust-2", "KRW", "NONE"));

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository, org.mockito.Mockito.times(2)).save(captor.capture());

        AuditLog first = captor.getAllValues().get(0);
        AuditLog second = captor.getAllValues().get(1);

        // Both after_state must equal the canonical form.
        String expected = "{\"status\":\"PENDING_KYC\"}";
        assertThat(first.getAfterState())
                .as("audit after_state byte-equal (call 1)")
                .isEqualTo(expected);
        assertThat(second.getAfterState())
                .as("audit after_state byte-equal (call 2)")
                .isEqualTo(expected);
        assertThat(first.getAfterState())
                .as("both calls produce identical JSON")
                .isEqualTo(second.getAfterState());
    }
}
