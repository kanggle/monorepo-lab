package com.example.finance.ledger.application;

import com.example.finance.ledger.application.port.outbound.ClockPort;
import com.example.finance.ledger.application.view.FxCostFlowConfigView;
import com.example.finance.ledger.domain.audit.AuditLog;
import com.example.finance.ledger.domain.audit.AuditLogRepository;
import com.example.finance.ledger.domain.error.LedgerErrors.CostFlowMethodInvalidException;
import com.example.finance.ledger.domain.journal.CostFlowMethod;
import com.example.finance.ledger.domain.journal.FxCostFlowConfig;
import com.example.finance.ledger.domain.journal.repository.FxCostFlowConfigRepository;
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
class SetFxCostFlowConfigUseCaseTest {

    private static final String TENANT = "finance";
    private static final String ACTOR = "user-1";
    private static final Instant NOW = Instant.parse("2026-02-01T10:00:00Z");

    @Mock FxCostFlowConfigRepository fxCostFlowConfigRepository;
    @Mock AuditLogRepository auditLogRepository;
    @Mock ClockPort clock;

    SetFxCostFlowConfigUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new SetFxCostFlowConfigUseCase(fxCostFlowConfigRepository, auditLogRepository, clock);
    }

    @Test
    @DisplayName("GET default — no row → WEIGHTED_AVERAGE with null audit fields")
    void getDefaultWhenAbsent() {
        GetFxCostFlowConfigUseCase getUseCase =
                new GetFxCostFlowConfigUseCase(fxCostFlowConfigRepository);
        when(fxCostFlowConfigRepository.findByTenantId(TENANT))
                .thenReturn(java.util.Optional.empty());

        FxCostFlowConfigView view = getUseCase.get(TENANT);

        assertThat(view.method()).isEqualTo(CostFlowMethod.WEIGHTED_AVERAGE);
        assertThat(view.updatedBy()).isNull();
        assertThat(view.updatedAt()).isNull();
    }

    @Test
    @DisplayName("upsert FIFO persists the config + audit row (FX_COST_FLOW_METHOD_SET); returns view")
    void upsertFifoPersistsAndAudits() {
        when(clock.now()).thenReturn(NOW);
        when(fxCostFlowConfigRepository.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(i -> i.getArgument(0));

        FxCostFlowConfigView view = useCase.set(
                new SetFxCostFlowConfigCommand(TENANT, "FIFO", ACTOR));

        assertThat(view.method()).isEqualTo(CostFlowMethod.FIFO);
        assertThat(view.updatedBy()).isEqualTo(ACTOR);
        assertThat(view.updatedAt()).isEqualTo(NOW);

        ArgumentCaptor<FxCostFlowConfig> config =
                ArgumentCaptor.forClass(FxCostFlowConfig.class);
        verify(fxCostFlowConfigRepository).save(config.capture());
        assertThat(config.getValue().tenantId()).isEqualTo(TENANT);
        assertThat(config.getValue().method()).isEqualTo(CostFlowMethod.FIFO);
        assertThat(config.getValue().updatedBy()).isEqualTo(ACTOR);
        assertThat(config.getValue().updatedAt()).isEqualTo(NOW);

        ArgumentCaptor<AuditLog> audit = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(audit.capture());
        assertThat(audit.getValue().getAction()).isEqualTo("FX_COST_FLOW_METHOD_SET");
        assertThat(audit.getValue().getActor()).isEqualTo(ACTOR);
        assertThat(audit.getValue().getTenantId()).isEqualTo(TENANT);
        assertThat(audit.getValue().getAfterState()).contains("FIFO");
    }

    @Test
    @DisplayName("unknown method (LIFO) → VALIDATION_ERROR before any persist (nothing written)")
    void unknownMethodRejected() {
        assertThatThrownBy(() -> useCase.set(
                new SetFxCostFlowConfigCommand(TENANT, "LIFO", ACTOR)))
                .isInstanceOf(CostFlowMethodInvalidException.class)
                .hasMessageContaining("LIFO");

        verify(fxCostFlowConfigRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(auditLogRepository);
        verify(clock, never()).now();
    }

    @Test
    @DisplayName("null method → VALIDATION_ERROR before any persist (nothing written)")
    void nullMethodRejected() {
        assertThatThrownBy(() -> useCase.set(
                new SetFxCostFlowConfigCommand(TENANT, null, ACTOR)))
                .isInstanceOf(CostFlowMethodInvalidException.class);

        verify(fxCostFlowConfigRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(auditLogRepository);
        verify(clock, never()).now();
    }
}
