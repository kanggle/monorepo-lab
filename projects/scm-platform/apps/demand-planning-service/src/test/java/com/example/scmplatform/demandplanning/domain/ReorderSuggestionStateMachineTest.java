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
    private static final UUID SUPPLIER_ID = UUID.randomUUID();
    private static final UUID EVENT_ID = UUID.randomUUID();

    private ReorderSuggestion suggested() {
        return ReorderSuggestion.raiseFromAlert(ID, "SKU-001", WAREHOUSE_ID, SUPPLIER_ID,
                100, EVENT_ID, 5, "scm", Instant.now());
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
