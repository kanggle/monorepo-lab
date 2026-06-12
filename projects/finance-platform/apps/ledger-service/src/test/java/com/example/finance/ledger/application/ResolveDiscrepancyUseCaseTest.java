package com.example.finance.ledger.application;

import com.example.finance.ledger.application.port.outbound.ClockPort;
import com.example.finance.ledger.application.view.DiscrepancyView;
import com.example.finance.ledger.domain.account.LedgerAccountCodes;
import com.example.finance.ledger.domain.audit.AuditLog;
import com.example.finance.ledger.domain.audit.AuditLogRepository;
import com.example.finance.ledger.domain.error.LedgerErrors.ReconciliationAlreadyResolvedException;
import com.example.finance.ledger.domain.error.LedgerErrors.ReconciliationDiscrepancyNotFoundException;
import com.example.finance.ledger.domain.money.Currency;
import com.example.finance.ledger.domain.reconciliation.DiscrepancyStatus;
import com.example.finance.ledger.domain.reconciliation.DiscrepancyType;
import com.example.finance.ledger.domain.reconciliation.ReconciliationDiscrepancy;
import com.example.finance.ledger.domain.reconciliation.ResolutionType;
import com.example.finance.ledger.domain.reconciliation.repository.ReconciliationRepository;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class ResolveDiscrepancyUseCaseTest {

    private static final String TENANT = "finance";
    private static final String CODE = LedgerAccountCodes.CASH_CLEARING;
    private static final Instant DETECTED = Instant.parse("2026-01-31T00:00:00Z");
    private static final Instant NOW = Instant.parse("2026-02-01T10:00:00Z");

    @Mock ReconciliationRepository reconciliationRepository;
    @Mock AuditLogRepository auditLogRepository;
    @Mock ClockPort clock;

    ResolveDiscrepancyUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ResolveDiscrepancyUseCase(
                reconciliationRepository, auditLogRepository, clock);
    }

    private static ReconciliationDiscrepancy openDiscrepancy() {
        return ReconciliationDiscrepancy.open("disc-1", TENANT, "stmt-1", CODE,
                DiscrepancyType.UNMATCHED_EXTERNAL, "R2", null,
                99_000L, 0L, Currency.KRW, DETECTED);
    }

    @Test
    @DisplayName("resolve flips OPEN→RESOLVED + resolution record + audit")
    void resolveOpen() {
        ReconciliationDiscrepancy discrepancy = openDiscrepancy();
        when(reconciliationRepository.findDiscrepancyById("disc-1", TENANT))
                .thenReturn(Optional.of(discrepancy));
        when(reconciliationRepository.saveDiscrepancy(any())).thenAnswer(i -> i.getArgument(0));
        when(clock.now()).thenReturn(NOW);

        DiscrepancyView view = useCase.resolve("disc-1", TENANT,
                ResolutionType.WRITTEN_OFF, "bank fee, below threshold", "user-1");

        assertThat(view.status()).isEqualTo(DiscrepancyStatus.RESOLVED);
        assertThat(view.resolutionType()).isEqualTo(ResolutionType.WRITTEN_OFF);
        assertThat(view.note()).isEqualTo("bank fee, below threshold");
        assertThat(view.resolvedBy()).isEqualTo("user-1");
        assertThat(view.resolvedAt()).isEqualTo(NOW);

        verify(reconciliationRepository).saveDiscrepancy(discrepancy);
        ArgumentCaptor<AuditLog> audit = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(audit.capture());
        assertThat(audit.getValue().getAction()).isEqualTo("RESOLVED");
    }

    @Test
    @DisplayName("a second resolve on an already-RESOLVED discrepancy → ReconciliationAlreadyResolvedException")
    void reResolveRejected() {
        ReconciliationDiscrepancy discrepancy = openDiscrepancy();
        discrepancy.resolve(ResolutionType.ACCEPTED, "first", "user-0", NOW);
        when(reconciliationRepository.findDiscrepancyById("disc-1", TENANT))
                .thenReturn(Optional.of(discrepancy));
        when(clock.now()).thenReturn(NOW);

        assertThatThrownBy(() -> useCase.resolve("disc-1", TENANT,
                ResolutionType.WRITTEN_OFF, "second", "user-1"))
                .isInstanceOf(ReconciliationAlreadyResolvedException.class);

        verify(reconciliationRepository, never()).saveDiscrepancy(any());
        verify(auditLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("an unknown discrepancy id → ReconciliationDiscrepancyNotFoundException")
    void notFound() {
        when(reconciliationRepository.findDiscrepancyById("missing", TENANT))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.resolve("missing", TENANT,
                ResolutionType.ACCEPTED, null, "user-1"))
                .isInstanceOf(ReconciliationDiscrepancyNotFoundException.class);

        verify(reconciliationRepository, never()).saveDiscrepancy(any());
        verify(auditLogRepository, never()).save(any());
    }
}
