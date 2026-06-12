package com.example.finance.ledger.application;

import com.example.finance.ledger.application.port.outbound.ClockPort;
import com.example.finance.ledger.domain.audit.AuditLog;
import com.example.finance.ledger.domain.audit.AuditLogRepository;
import com.example.finance.ledger.domain.error.LedgerErrors.AccountingPeriodInvalidWindowException;
import com.example.finance.ledger.domain.error.LedgerErrors.AccountingPeriodOverlapException;
import com.example.finance.ledger.domain.period.AccountingPeriod;
import com.example.finance.ledger.domain.period.PeriodStatus;
import com.example.finance.ledger.domain.period.repository.AccountingPeriodRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class OpenAccountingPeriodUseCaseTest {

    private static final String TENANT = "finance";
    private static final Instant FROM = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-02-01T00:00:00Z");
    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");

    @Mock AccountingPeriodRepository periodRepository;
    @Mock AuditLogRepository auditLogRepository;
    @Mock ClockPort clock;

    OpenAccountingPeriodUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new OpenAccountingPeriodUseCase(periodRepository, auditLogRepository, clock);
    }

    @Test
    @DisplayName("a non-overlapping window opens an OPEN period, saves it and audits OPENED")
    void opensAndAudits() {
        when(periodRepository.findOverlapping(TENANT, FROM, TO)).thenReturn(List.of());
        when(periodRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(clock.now()).thenReturn(NOW);

        AccountingPeriod opened = useCase.open(TENANT, FROM, TO, "user-1");

        assertThat(opened.status()).isEqualTo(PeriodStatus.OPEN);
        ArgumentCaptor<AccountingPeriod> saved = ArgumentCaptor.forClass(AccountingPeriod.class);
        verify(periodRepository).save(saved.capture());
        assertThat(saved.getValue().from()).isEqualTo(FROM);
        assertThat(saved.getValue().to()).isEqualTo(TO);

        ArgumentCaptor<AuditLog> audit = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(audit.capture());
        assertThat(audit.getValue().getAction()).isEqualTo("OPENED");
        assertThat(audit.getValue().getActor()).isEqualTo("user-1");
    }

    @Test
    @DisplayName("a window overlapping an existing period → AccountingPeriodOverlapException, no save")
    void overlapRejected() {
        when(periodRepository.findOverlapping(TENANT, FROM, TO))
                .thenReturn(List.of(AccountingPeriod.open("p-existing", TENANT, FROM, TO)));

        assertThatThrownBy(() -> useCase.open(TENANT, FROM, TO, "user-1"))
                .isInstanceOf(AccountingPeriodOverlapException.class);

        verify(periodRepository, never()).save(any());
        verify(auditLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("from >= to is rejected at the factory before any overlap query")
    void invalidWindowRejected() {
        assertThatThrownBy(() -> useCase.open(TENANT, TO, FROM, "user-1"))
                .isInstanceOf(AccountingPeriodInvalidWindowException.class);

        verify(periodRepository, never()).findOverlapping(any(), any(), any());
        verify(periodRepository, never()).save(any());
    }
}
