package com.example.scmplatform.demandplanning.domain;

import com.example.scmplatform.demandplanning.domain.error.InvalidSuggestionStateException;
import com.example.scmplatform.demandplanning.domain.model.ReorderSuggestion;
import com.example.scmplatform.demandplanning.domain.model.SuggestionStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReorderSuggestionStateMachineTest {

    private static final UUID ID = UUID.randomUUID();
    private static final UUID WAREHOUSE_ID = UUID.randomUUID();
    // ADR-MONO-050 D9: warehouse + supplier CODES (String).
    private static final String WAREHOUSE_CODE = "WH-SEOUL-01";
    private static final String SUPPLIER_ID = "SUP-0043";
    private static final UUID EVENT_ID = UUID.randomUUID();

    private ReorderSuggestion suggested() {
        return ReorderSuggestion.raiseFromAlert(ID, "SKU-001", WAREHOUSE_ID, WAREHOUSE_CODE, SUPPLIER_ID,
                100, EVENT_ID, 5, 10, "scm", Instant.now());
    }

    // --- TASK-SCM-BE-062: reorderPointAtTrigger snapshot --------------------------------

    @Test
    void raiseFromAlert_carriesReorderPointAtTrigger() {
        assertThat(suggested().getReorderPointAtTrigger()).isEqualTo(10);
    }

    @Test
    void raiseFromBatch_carriesReorderPointAtTrigger_orNull() {
        ReorderSuggestion withPolicy = ReorderSuggestion.raiseFromBatch(ID, "SKU-001", WAREHOUSE_ID,
                WAREHOUSE_CODE, null, SUPPLIER_ID, 100, 3, 20, "scm", Instant.now());
        ReorderSuggestion withoutPolicy = ReorderSuggestion.raiseFromBatch(ID, "SKU-001", WAREHOUSE_ID,
                WAREHOUSE_CODE, null, SUPPLIER_ID, 100, 3, null, "scm", Instant.now());
        assertThat(withPolicy.getReorderPointAtTrigger()).isEqualTo(20);
        assertThat(withoutPolicy.getReorderPointAtTrigger()).isNull();
    }

    @Test
    void transitions_neverChangeReorderPointAtTrigger() {
        ReorderSuggestion approvedThenMaterialized = suggested();
        approvedThenMaterialized.approve(Instant.now());
        approvedThenMaterialized.materialize(UUID.randomUUID(), Instant.now());
        assertThat(approvedThenMaterialized.getReorderPointAtTrigger()).isEqualTo(10);

        ReorderSuggestion dismissed = suggested();
        dismissed.dismiss(Instant.now());
        assertThat(dismissed.getReorderPointAtTrigger()).isEqualTo(10);
    }

    @Test
    void reconstitute_preservesReorderPointAtTrigger_andNull() {
        Instant now = Instant.now();
        ReorderSuggestion restored = ReorderSuggestion.reconstitute(ID, "SKU-001", WAREHOUSE_ID,
                WAREHOUSE_CODE, "WMS_WAREHOUSE", SUPPLIER_ID, 100, SuggestionStatus.APPROVED,
                com.example.scmplatform.demandplanning.domain.model.SuggestionSource.ALERT,
                EVENT_ID, 5, 10, null, "scm", 1, now, now);
        ReorderSuggestion legacy = ReorderSuggestion.reconstitute(ID, "SKU-001", WAREHOUSE_ID,
                WAREHOUSE_CODE, "WMS_WAREHOUSE", SUPPLIER_ID, 100, SuggestionStatus.SUGGESTED,
                com.example.scmplatform.demandplanning.domain.model.SuggestionSource.ALERT,
                EVENT_ID, 5, null, null, "scm", 0, now, now);
        assertThat(restored.getReorderPointAtTrigger()).isEqualTo(10);
        assertThat(legacy.getReorderPointAtTrigger()).isNull();
    }

    @Test
    void raiseFromAlert_createsSuggested() {
        ReorderSuggestion s = suggested();
        assertThat(s.getStatus()).isEqualTo(SuggestionStatus.SUGGESTED);
        assertThat(s.getVersion()).isEqualTo(0);
    }

    @Test
    void approve_transitionsToApproved() {
        ReorderSuggestion s = suggested();
        s.approve(Instant.now());
        assertThat(s.getStatus()).isEqualTo(SuggestionStatus.APPROVED);
        assertThat(s.getVersion()).isEqualTo(1);
    }

    @Test
    void dismiss_fromSuggested_transitionsToDismissed() {
        ReorderSuggestion s = suggested();
        s.dismiss(Instant.now());
        assertThat(s.getStatus()).isEqualTo(SuggestionStatus.DISMISSED);
    }

    @Test
    void dismiss_fromApproved_transitionsToDismissed() {
        ReorderSuggestion s = suggested();
        s.approve(Instant.now());
        s.dismiss(Instant.now());
        assertThat(s.getStatus()).isEqualTo(SuggestionStatus.DISMISSED);
    }

    @Test
    void materialize_fromApproved_transitionsToMaterialized() {
        ReorderSuggestion s = suggested();
        s.approve(Instant.now());
        UUID poId = UUID.randomUUID();
        s.materialize(poId, Instant.now());
        assertThat(s.getStatus()).isEqualTo(SuggestionStatus.MATERIALIZED);
        assertThat(s.getMaterializedPoId()).isEqualTo(poId);
    }

    @Test
    void approve_fromApproved_throws() {
        ReorderSuggestion s = suggested();
        s.approve(Instant.now());
        assertThatThrownBy(() -> s.approve(Instant.now()))
                .isInstanceOf(InvalidSuggestionStateException.class);
    }

    @Test
    void approve_fromDismissed_throws() {
        ReorderSuggestion s = suggested();
        s.dismiss(Instant.now());
        assertThatThrownBy(() -> s.approve(Instant.now()))
                .isInstanceOf(InvalidSuggestionStateException.class);
    }

    @Test
    void dismiss_fromMaterialized_throws() {
        ReorderSuggestion s = suggested();
        s.approve(Instant.now());
        s.materialize(UUID.randomUUID(), Instant.now());
        assertThatThrownBy(() -> s.dismiss(Instant.now()))
                .isInstanceOf(InvalidSuggestionStateException.class);
    }

    @Test
    void materialize_fromSuggested_throws() {
        ReorderSuggestion s = suggested();
        assertThatThrownBy(() -> s.materialize(UUID.randomUUID(), Instant.now()))
                .isInstanceOf(InvalidSuggestionStateException.class);
    }

    @Test
    void isTerminal_materializedAndDismissed() {
        assertThat(SuggestionStatus.MATERIALIZED.isTerminal()).isTrue();
        assertThat(SuggestionStatus.DISMISSED.isTerminal()).isTrue();
        assertThat(SuggestionStatus.SUGGESTED.isTerminal()).isFalse();
        assertThat(SuggestionStatus.APPROVED.isTerminal()).isFalse();
    }
}
