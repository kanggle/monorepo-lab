package com.example.scmplatform.demandplanning.application;

import com.example.scmplatform.demandplanning.application.port.outbound.ReorderSuggestionPort;
import com.example.scmplatform.demandplanning.application.port.outbound.SkuSupplierMappingPort;
import com.example.scmplatform.demandplanning.application.usecase.SuggestionApprovalTxn;
import com.example.scmplatform.demandplanning.domain.error.InvalidSuggestionStateException;
import com.example.scmplatform.demandplanning.domain.error.SkuSupplierUnmappedException;
import com.example.scmplatform.demandplanning.domain.model.ReorderSuggestion;
import com.example.scmplatform.demandplanning.domain.model.SkuSupplierMapping;
import com.example.scmplatform.demandplanning.domain.model.SuggestionSource;
import com.example.scmplatform.demandplanning.domain.model.SuggestionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class SuggestionApprovalTxnTest {

    @Mock ReorderSuggestionPort suggestionPort;
    @Mock SkuSupplierMappingPort mappingPort;

    private SuggestionApprovalTxn txn() {
        return new SuggestionApprovalTxn(suggestionPort, mappingPort);
    }

    static final String TENANT = "scm";
    static final String SKU = "SKU-APPLE-001";
    static final UUID SUGGESTION_ID = UUID.fromString("0192cccc-0000-0000-0000-000000000001");
    static final UUID WAREHOUSE_ID = UUID.fromString("0192cccc-0000-0000-0000-000000000002");
    static final UUID SUPPLIER_ID = UUID.fromString("0192cccc-0000-0000-0000-000000000003");

    private ReorderSuggestion suggested() {
        return ReorderSuggestion.raiseFromAlert(
                SUGGESTION_ID, SKU, WAREHOUSE_ID, SUPPLIER_ID, 100,
                UUID.randomUUID(), 5, TENANT, Instant.now());
    }

    private SkuSupplierMapping mapping() {
        return new SkuSupplierMapping(SKU, SUPPLIER_ID, 100, 7, "KRW", TENANT);
    }

    @Test
    void prepareApprove_transitionsSuggestedToApproved_andReturnsResolvedParams() {
        ReorderSuggestion suggestion = suggested();
        when(suggestionPort.findById(SUGGESTION_ID)).thenReturn(Optional.of(suggestion));
        when(mappingPort.findBySkuCode(TENANT, SKU)).thenReturn(Optional.of(mapping()));
        when(suggestionPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SuggestionApprovalTxn.ApprovalPlan plan = txn().prepareApprove(SUGGESTION_ID);

        assertThat(plan.alreadyMaterialized()).isFalse();
        assertThat(plan.supplierId()).isEqualTo(SUPPLIER_ID);
        assertThat(plan.currency()).isEqualTo("KRW");
        assertThat(plan.skuCode()).isEqualTo(SKU);
        assertThat(plan.quantity()).isEqualTo(100);

        ArgumentCaptor<ReorderSuggestion> saved = ArgumentCaptor.forClass(ReorderSuggestion.class);
        verify(suggestionPort).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(SuggestionStatus.APPROVED);
    }

    @Test
    void prepareApprove_unmappedSku_throws422_andLeavesSuggestionUntouched() {
        ReorderSuggestion suggestion = suggested();
        when(suggestionPort.findById(SUGGESTION_ID)).thenReturn(Optional.of(suggestion));
        when(mappingPort.findBySkuCode(TENANT, SKU)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> txn().prepareApprove(SUGGESTION_ID))
                .isInstanceOf(SkuSupplierUnmappedException.class);

        // No transition persisted — suggestion stays SUGGESTED (AC-3).
        assertThat(suggestion.getStatus()).isEqualTo(SuggestionStatus.SUGGESTED);
        verify(suggestionPort, never()).save(any());
    }

    @Test
    void prepareApprove_dismissedSuggestion_throws422() {
        ReorderSuggestion dismissed = ReorderSuggestion.reconstitute(
                SUGGESTION_ID, SKU, WAREHOUSE_ID, SUPPLIER_ID, 100,
                SuggestionStatus.DISMISSED, SuggestionSource.ALERT, null, 5, null,
                TENANT, 1, Instant.now(), Instant.now());
        when(suggestionPort.findById(SUGGESTION_ID)).thenReturn(Optional.of(dismissed));

        assertThatThrownBy(() -> txn().prepareApprove(SUGGESTION_ID))
                .isInstanceOf(InvalidSuggestionStateException.class);
        verify(suggestionPort, never()).save(any());
    }

    @Test
    void prepareApprove_alreadyMaterialized_shortCircuits_withoutMappingLookup() {
        UUID poId = UUID.randomUUID();
        ReorderSuggestion materialized = ReorderSuggestion.reconstitute(
                SUGGESTION_ID, SKU, WAREHOUSE_ID, SUPPLIER_ID, 100,
                SuggestionStatus.MATERIALIZED, SuggestionSource.ALERT, null, 5, poId,
                TENANT, 2, Instant.now(), Instant.now());
        when(suggestionPort.findById(SUGGESTION_ID)).thenReturn(Optional.of(materialized));

        SuggestionApprovalTxn.ApprovalPlan plan = txn().prepareApprove(SUGGESTION_ID);

        assertThat(plan.alreadyMaterialized()).isTrue();
        assertThat(plan.existingPoId()).isEqualTo(poId);
        verify(suggestionPort, never()).save(any());
    }

    @Test
    void prepareApprove_alreadyApproved_reResolvesWithoutReTransition() {
        // A prior failed attempt left the suggestion APPROVED; retry re-resolves
        // the mapping but does not re-save the (already APPROVED) suggestion.
        ReorderSuggestion approved = ReorderSuggestion.reconstitute(
                SUGGESTION_ID, SKU, WAREHOUSE_ID, SUPPLIER_ID, 100,
                SuggestionStatus.APPROVED, SuggestionSource.ALERT, null, 5, null,
                TENANT, 1, Instant.now(), Instant.now());
        when(suggestionPort.findById(SUGGESTION_ID)).thenReturn(Optional.of(approved));
        when(mappingPort.findBySkuCode(TENANT, SKU)).thenReturn(Optional.of(mapping()));

        SuggestionApprovalTxn.ApprovalPlan plan = txn().prepareApprove(SUGGESTION_ID);

        assertThat(plan.alreadyMaterialized()).isFalse();
        assertThat(plan.supplierId()).isEqualTo(SUPPLIER_ID);
        verify(suggestionPort, never()).save(any());
    }

    @Test
    void completeMaterialize_transitionsApprovedToMaterialized() {
        ReorderSuggestion approved = ReorderSuggestion.reconstitute(
                SUGGESTION_ID, SKU, WAREHOUSE_ID, SUPPLIER_ID, 100,
                SuggestionStatus.APPROVED, SuggestionSource.ALERT, null, 5, null,
                TENANT, 1, Instant.now(), Instant.now());
        when(suggestionPort.findById(SUGGESTION_ID)).thenReturn(Optional.of(approved));
        when(suggestionPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
        UUID poId = UUID.randomUUID();

        UUID result = txn().completeMaterialize(SUGGESTION_ID, poId.toString());

        assertThat(result).isEqualTo(poId);
        ArgumentCaptor<ReorderSuggestion> saved = ArgumentCaptor.forClass(ReorderSuggestion.class);
        verify(suggestionPort).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(SuggestionStatus.MATERIALIZED);
        assertThat(saved.getValue().getMaterializedPoId()).isEqualTo(poId);
    }

    @Test
    void completeMaterialize_alreadyMaterialized_isIdempotent() {
        UUID poId = UUID.randomUUID();
        ReorderSuggestion materialized = ReorderSuggestion.reconstitute(
                SUGGESTION_ID, SKU, WAREHOUSE_ID, SUPPLIER_ID, 100,
                SuggestionStatus.MATERIALIZED, SuggestionSource.ALERT, null, 5, poId,
                TENANT, 2, Instant.now(), Instant.now());
        when(suggestionPort.findById(SUGGESTION_ID)).thenReturn(Optional.of(materialized));

        UUID result = txn().completeMaterialize(SUGGESTION_ID, UUID.randomUUID().toString());

        assertThat(result).isEqualTo(poId);
        verify(suggestionPort, never()).save(any());
    }
}
