package com.example.finance.ledger.application;

import com.example.finance.ledger.application.port.outbound.ClockPort;
import com.example.finance.ledger.application.view.FxToleranceView;
import com.example.finance.ledger.domain.audit.AuditLog;
import com.example.finance.ledger.domain.audit.AuditLogRepository;
import com.example.finance.ledger.domain.error.LedgerErrors.FxToleranceInvalidException;
import com.example.finance.ledger.domain.reconciliation.ReconciliationFxToleranceConfig;
import com.example.finance.ledger.domain.reconciliation.repository.ReconciliationFxToleranceRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class SetFxToleranceUseCaseTest {

    private static final String TENANT = "finance";
    private static final String ACTOR = "user-1";
    private static final Instant NOW = Instant.parse("2026-02-01T10:00:00Z");

    @Mock ReconciliationFxToleranceRepository fxToleranceRepository;
    @Mock AuditLogRepository auditLogRepository;
    @Mock ClockPort clock;

    SetFxToleranceUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new SetFxToleranceUseCase(fxToleranceRepository, auditLogRepository, clock);
    }

    @Test
    @DisplayName("upsert persists the config + an audit row (updated_by = actor); returns the view")
    void upsertPersistsAndAudits() {
        when(clock.now()).thenReturn(NOW);
        when(fxToleranceRepository.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(i -> i.getArgument(0));

        FxToleranceView view = useCase.set(new SetFxToleranceCommand(TENANT, 100, 50L, ACTOR));

        assertThat(view.toleranceBps()).isEqualTo(100);
        assertThat(view.floorMinor()).isEqualTo(50L);
        assertThat(view.updatedBy()).isEqualTo(ACTOR);
        assertThat(view.updatedAt()).isEqualTo(NOW);

        ArgumentCaptor<ReconciliationFxToleranceConfig> config =
                ArgumentCaptor.forClass(ReconciliationFxToleranceConfig.class);
        verify(fxToleranceRepository).save(config.capture());
        assertThat(config.getValue().tenantId()).isEqualTo(TENANT);
        assertThat(config.getValue().toleranceBps()).isEqualTo(100);
        assertThat(config.getValue().floorMinor()).isEqualTo(50L);
        assertThat(config.getValue().updatedBy()).isEqualTo(ACTOR);
        assertThat(config.getValue().updatedAt()).isEqualTo(NOW);

        ArgumentCaptor<AuditLog> audit = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(audit.capture());
        assertThat(audit.getValue().getAction()).isEqualTo("FX_TOLERANCE_SET");
        assertThat(audit.getValue().getActor()).isEqualTo(ACTOR);
        assertThat(audit.getValue().getTenantId()).isEqualTo(TENANT);
    }

    @Test
    @DisplayName("negative bps → VALIDATION_ERROR before any persist (nothing written)")
    void negativeBpsRejected() {
        assertThatThrownBy(() -> useCase.set(new SetFxToleranceCommand(TENANT, -1, 0L, ACTOR)))
                .isInstanceOf(FxToleranceInvalidException.class)
                .hasMessageContaining("toleranceBps");

        verify(fxToleranceRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(auditLogRepository);
        verify(clock, never()).now();
    }

    @Test
    @DisplayName("negative floor → VALIDATION_ERROR before any persist (nothing written)")
    void negativeFloorRejected() {
        assertThatThrownBy(() -> useCase.set(new SetFxToleranceCommand(TENANT, 0, -1L, ACTOR)))
                .isInstanceOf(FxToleranceInvalidException.class)
                .hasMessageContaining("floorMinor");

        verify(fxToleranceRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(auditLogRepository);
        verify(clock, never()).now();
    }
}
