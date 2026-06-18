package com.example.finance.ledger.application;

import com.example.finance.ledger.application.port.outbound.ClockPort;
import com.example.finance.ledger.application.view.FxRateOverrideView;
import com.example.finance.ledger.domain.audit.AuditLog;
import com.example.finance.ledger.domain.audit.AuditLogRepository;
import com.example.finance.ledger.domain.error.LedgerErrors.FxRateOverrideInvalidException;
import com.example.finance.ledger.domain.journal.FxRateOverride;
import com.example.finance.ledger.domain.journal.repository.FxRateOverrideRepository;
import com.example.finance.ledger.domain.money.Currency;
import com.example.finance.ledger.domain.money.LedgerReportingCurrency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link SetFxRateOverrideUseCase} + {@link GetFxRateOverrideUseCase} (28th
 * increment — TASK-FIN-BE-042). Pure (Mockito STRICT_STUBS). Proves the upsert + audit
 * ({@code FX_RATE_OVERRIDE_SET}), the read (present + absent), tenant-scoping, and the
 * non-positive / invalid-currency rejection (nothing written).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class SetFxRateOverrideUseCaseTest {

    private static final String TENANT = "finance";
    private static final String BASE = LedgerReportingCurrency.BASE.code(); // KRW
    private static final String ACTOR = "user-1";
    private static final Instant NOW = Instant.parse("2026-02-01T10:00:00Z");

    @Mock FxRateOverrideRepository fxRateOverrideRepository;
    @Mock AuditLogRepository auditLogRepository;
    @Mock ClockPort clock;

    SetFxRateOverrideUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new SetFxRateOverrideUseCase(fxRateOverrideRepository, auditLogRepository, clock);
    }

    @Test
    @DisplayName("upsert persists the override + audit row (FX_RATE_OVERRIDE_SET); returns view")
    void upsertPersistsAndAudits() {
        when(clock.now()).thenReturn(NOW);
        when(fxRateOverrideRepository.save(ArgumentMatchers.any()))
                .thenAnswer(i -> i.getArgument(0));

        FxRateOverrideView view = useCase.set(
                new SetFxRateOverrideCommand(TENANT, BASE, "USD", new BigDecimal("1325.5"), ACTOR));

        assertThat(view.present()).isTrue();
        assertThat(view.baseCurrency()).isEqualTo("KRW");
        assertThat(view.foreignCurrency()).isEqualTo("USD");
        assertThat(view.rate()).isEqualByComparingTo("1325.5");
        assertThat(view.updatedBy()).isEqualTo(ACTOR);
        assertThat(view.updatedAt()).isEqualTo(NOW);

        ArgumentCaptor<FxRateOverride> override = ArgumentCaptor.forClass(FxRateOverride.class);
        verify(fxRateOverrideRepository).save(override.capture());
        assertThat(override.getValue().tenantId()).isEqualTo(TENANT);
        assertThat(override.getValue().baseCurrency()).isEqualTo("KRW");
        assertThat(override.getValue().foreignCurrency()).isEqualTo("USD");
        assertThat(override.getValue().rate()).isEqualByComparingTo("1325.5");
        assertThat(override.getValue().updatedBy()).isEqualTo(ACTOR);

        ArgumentCaptor<AuditLog> audit = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(audit.capture());
        assertThat(audit.getValue().getAction()).isEqualTo("FX_RATE_OVERRIDE_SET");
        assertThat(audit.getValue().getActor()).isEqualTo(ACTOR);
        assertThat(audit.getValue().getTenantId()).isEqualTo(TENANT);
        assertThat(audit.getValue().getAfterState()).contains("KRW/USD").contains("1325.5");
    }

    @Test
    @DisplayName("non-positive rate (0) → VALIDATION_ERROR before any persist (nothing written)")
    void zeroRateRejected() {
        assertThatThrownBy(() -> useCase.set(
                new SetFxRateOverrideCommand(TENANT, BASE, "USD", BigDecimal.ZERO, ACTOR)))
                .isInstanceOf(FxRateOverrideInvalidException.class);

        verify(fxRateOverrideRepository, never()).save(ArgumentMatchers.any());
        verifyNoInteractions(auditLogRepository);
        verify(clock, never()).now();
    }

    @Test
    @DisplayName("negative rate → VALIDATION_ERROR before any persist (nothing written)")
    void negativeRateRejected() {
        assertThatThrownBy(() -> useCase.set(
                new SetFxRateOverrideCommand(TENANT, BASE, "USD", new BigDecimal("-1"), ACTOR)))
                .isInstanceOf(FxRateOverrideInvalidException.class);

        verify(fxRateOverrideRepository, never()).save(ArgumentMatchers.any());
        verifyNoInteractions(auditLogRepository);
    }

    @Test
    @DisplayName("null rate → VALIDATION_ERROR before any persist (nothing written)")
    void nullRateRejected() {
        assertThatThrownBy(() -> useCase.set(
                new SetFxRateOverrideCommand(TENANT, BASE, "USD", null, ACTOR)))
                .isInstanceOf(FxRateOverrideInvalidException.class);

        verify(fxRateOverrideRepository, never()).save(ArgumentMatchers.any());
        verifyNoInteractions(auditLogRepository);
    }

    @Test
    @DisplayName("unknown currency → VALIDATION_ERROR before any persist (nothing written)")
    void unknownCurrencyRejected() {
        assertThatThrownBy(() -> useCase.set(
                new SetFxRateOverrideCommand(TENANT, BASE, "ZZZ", new BigDecimal("10"), ACTOR)))
                .isInstanceOf(FxRateOverrideInvalidException.class);

        verify(fxRateOverrideRepository, never()).save(ArgumentMatchers.any());
        verifyNoInteractions(auditLogRepository);
    }

    @Test
    @DisplayName("base == foreign → VALIDATION_ERROR (a self-pair contract rate is meaningless)")
    void samePairRejected() {
        assertThatThrownBy(() -> useCase.set(
                new SetFxRateOverrideCommand(TENANT, BASE, "KRW", new BigDecimal("1"), ACTOR)))
                .isInstanceOf(FxRateOverrideInvalidException.class);

        verify(fxRateOverrideRepository, never()).save(ArgumentMatchers.any());
        verifyNoInteractions(auditLogRepository);
    }

    @Test
    @DisplayName("GET present → projects the persisted override (tenant-scoped lookup)")
    void getPresentProjectsRow() {
        GetFxRateOverrideUseCase getUseCase = new GetFxRateOverrideUseCase(fxRateOverrideRepository);
        FxRateOverride row = FxRateOverride.of(
                TENANT, LedgerReportingCurrency.BASE, Currency.USD, new BigDecimal("1325.5"), ACTOR, NOW);
        when(fxRateOverrideRepository.findOverride(TENANT, LedgerReportingCurrency.BASE, Currency.USD))
                .thenReturn(Optional.of(row));

        FxRateOverrideView view = getUseCase.get(TENANT, "USD");

        assertThat(view.present()).isTrue();
        assertThat(view.rate()).isEqualByComparingTo("1325.5");
        assertThat(view.foreignCurrency()).isEqualTo("USD");
        assertThat(view.updatedBy()).isEqualTo(ACTOR);
    }

    @Test
    @DisplayName("GET absent → 'none' view (present=false), resolution falls through to the feed")
    void getAbsentReturnsNone() {
        GetFxRateOverrideUseCase getUseCase = new GetFxRateOverrideUseCase(fxRateOverrideRepository);
        when(fxRateOverrideRepository.findOverride(TENANT, LedgerReportingCurrency.BASE, Currency.USD))
                .thenReturn(Optional.empty());

        FxRateOverrideView view = getUseCase.get(TENANT, "USD");

        assertThat(view.present()).isFalse();
        assertThat(view.rate()).isNull();
        assertThat(view.updatedBy()).isNull();
        assertThat(view.baseCurrency()).isEqualTo("KRW");
        assertThat(view.foreignCurrency()).isEqualTo("USD");
    }

    @Test
    @DisplayName("GET unknown currency → VALIDATION_ERROR")
    void getUnknownCurrencyRejected() {
        GetFxRateOverrideUseCase getUseCase = new GetFxRateOverrideUseCase(fxRateOverrideRepository);

        assertThatThrownBy(() -> getUseCase.get(TENANT, "ZZZ"))
                .isInstanceOf(FxRateOverrideInvalidException.class);
    }
}
