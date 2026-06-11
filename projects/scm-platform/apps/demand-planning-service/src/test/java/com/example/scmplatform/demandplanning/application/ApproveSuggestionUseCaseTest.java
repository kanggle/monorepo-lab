package com.example.scmplatform.demandplanning.application;

import com.example.scmplatform.demandplanning.application.port.outbound.ProcurementDraftPoPort;
import com.example.scmplatform.demandplanning.application.usecase.ApproveSuggestionUseCase;
import com.example.scmplatform.demandplanning.application.usecase.SuggestionApprovalTxn;
import com.example.scmplatform.demandplanning.domain.error.ProcurementUnavailableException;
import com.example.scmplatform.demandplanning.domain.model.SuggestionStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class ApproveSuggestionUseCaseTest {

    @Mock SuggestionApprovalTxn approvalTxn;
    @Mock ProcurementDraftPoPort procurementPort;

    private ApproveSuggestionUseCase useCase;

    static final UUID SUGGESTION_ID = UUID.fromString("0192cccc-0000-0000-0000-000000000001");
    static final UUID SUPPLIER_ID = UUID.fromString("0192cccc-0000-0000-0000-000000000003");
    static final String TOKEN = "Bearer test-token";

    @BeforeEach
    void setUp() {
        useCase = new ApproveSuggestionUseCase(approvalTxn, procurementPort, new SimpleMeterRegistry());
    }

    private SuggestionApprovalTxn.ApprovalPlan proceedPlan() {
        return SuggestionApprovalTxn.ApprovalPlan.proceed(
                SUGGESTION_ID, SUPPLIER_ID, "KRW", "SKU-APPLE-001", 100);
    }

    @Test
    void approve_happyPath_callsProcurementThenMaterializes() {
        UUID poId = UUID.randomUUID();
        when(approvalTxn.prepareApprove(SUGGESTION_ID)).thenReturn(proceedPlan());
        when(procurementPort.createDraftFromSuggestion(any(), eq(TOKEN)))
                .thenReturn(new ProcurementDraftPoPort.DraftPoResult(poId.toString(), "DRAFT"));
        when(approvalTxn.completeMaterialize(SUGGESTION_ID, poId.toString())).thenReturn(poId);

        ApproveSuggestionUseCase.ApproveResult result = useCase.approve(SUGGESTION_ID, TOKEN);

        assertThat(result.status()).isEqualTo(SuggestionStatus.MATERIALIZED);
        assertThat(result.poId()).isEqualTo(poId);
        assertThat(result.poStatus()).isEqualTo("DRAFT");

        // The procurement command carried the resolved supplier + sku + qty.
        verify(procurementPort).createDraftFromSuggestion(
                argThat(cmd -> cmd.sourceSuggestionId().equals(SUGGESTION_ID)
                        && cmd.supplierId().equals(SUPPLIER_ID)
                        && cmd.currency().equals("KRW")
                        && cmd.skuCode().equals("SKU-APPLE-001")
                        && cmd.quantity() == 100),
                eq(TOKEN));
    }

    @Test
    void approve_alreadyMaterialized_returnsExistingPoId_withoutProcurementCall() {
        UUID existingPoId = UUID.randomUUID();
        when(approvalTxn.prepareApprove(SUGGESTION_ID))
                .thenReturn(SuggestionApprovalTxn.ApprovalPlan.alreadyMaterialized(existingPoId));

        ApproveSuggestionUseCase.ApproveResult result = useCase.approve(SUGGESTION_ID, TOKEN);

        assertThat(result.status()).isEqualTo(SuggestionStatus.MATERIALIZED);
        assertThat(result.poId()).isEqualTo(existingPoId);
        verify(procurementPort, never()).createDraftFromSuggestion(any(), any());
        verify(approvalTxn, never()).completeMaterialize(any(), any());
    }

    @Test
    void approve_procurementFails_propagates_andDoesNotMaterialize() {
        when(approvalTxn.prepareApprove(SUGGESTION_ID)).thenReturn(proceedPlan());
        when(procurementPort.createDraftFromSuggestion(any(), eq(TOKEN)))
                .thenThrow(new ProcurementUnavailableException("connection refused"));

        assertThatThrownBy(() -> useCase.approve(SUGGESTION_ID, TOKEN))
                .isInstanceOf(ProcurementUnavailableException.class);

        // Suggestion stays APPROVED (Tx-1 committed); materialize never runs.
        verify(approvalTxn, never()).completeMaterialize(any(), any());
    }
}
