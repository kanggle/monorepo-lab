package com.wms.inbound.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wms.inbound.domain.exception.AsnAlreadyClosedException;
import com.wms.inbound.domain.exception.StateTransitionInvalidException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AsnTest {

    private static final Instant NOW = Instant.parse("2026-04-29T10:00:00Z");
    private static final String ACTOR = "test-actor";

    // -------------------------------------------------------------------------
    // startInspection
    // -------------------------------------------------------------------------

    @Test
    void startInspection_fromCreated_transitionsToInspecting() {
        Asn asn = asn(AsnStatus.CREATED);
        asn.startInspection(NOW, ACTOR);
        assertThat(asn.getStatus()).isEqualTo(AsnStatus.INSPECTING);
        assertThat(asn.getUpdatedBy()).isEqualTo(ACTOR);
        assertThat(asn.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void startInspection_fromInspecting_throwsStateTransitionInvalid() {
        Asn asn = asn(AsnStatus.INSPECTING);
        assertThatThrownBy(() -> asn.startInspection(NOW, ACTOR))
                .isInstanceOf(StateTransitionInvalidException.class);
    }

    @Test
    void startInspection_fromInspected_throwsStateTransitionInvalid() {
        Asn asn = asn(AsnStatus.INSPECTED);
        assertThatThrownBy(() -> asn.startInspection(NOW, ACTOR))
                .isInstanceOf(StateTransitionInvalidException.class);
    }

    // -------------------------------------------------------------------------
    // completeInspection
    // -------------------------------------------------------------------------

    @Test
    void completeInspection_fromInspecting_transitionsToInspected() {
        Asn asn = asn(AsnStatus.INSPECTING);
        asn.completeInspection(NOW, ACTOR);
        assertThat(asn.getStatus()).isEqualTo(AsnStatus.INSPECTED);
        assertThat(asn.getUpdatedBy()).isEqualTo(ACTOR);
    }

    @Test
    void completeInspection_fromCreated_throwsStateTransitionInvalid() {
        Asn asn = asn(AsnStatus.CREATED);
        assertThatThrownBy(() -> asn.completeInspection(NOW, ACTOR))
                .isInstanceOf(StateTransitionInvalidException.class);
    }

    // -------------------------------------------------------------------------
    // cancel
    // -------------------------------------------------------------------------

    @Test
    void cancel_fromCreated_transitionsToCancelled() {
        Asn asn = asn(AsnStatus.CREATED);
        asn.cancel("wrong delivery", NOW, ACTOR);
        assertThat(asn.getStatus()).isEqualTo(AsnStatus.CANCELLED);
    }

    @Test
    void cancel_fromInspecting_transitionsToCancelled() {
        Asn asn = asn(AsnStatus.INSPECTING);
        asn.cancel("reason", NOW, ACTOR);
        assertThat(asn.getStatus()).isEqualTo(AsnStatus.CANCELLED);
    }

    @Test
    void cancel_fromCancelled_throwsAsnAlreadyClosed() {
        Asn asn = asn(AsnStatus.CANCELLED);
        assertThatThrownBy(() -> asn.cancel("reason", NOW, ACTOR))
                .isInstanceOf(AsnAlreadyClosedException.class);
    }

    @Test
    void cancel_fromClosed_throwsAsnAlreadyClosed() {
        Asn asn = asn(AsnStatus.CLOSED);
        assertThatThrownBy(() -> asn.cancel("reason", NOW, ACTOR))
                .isInstanceOf(AsnAlreadyClosedException.class);
    }

    @Test
    void cancel_fromInspected_throwsStateTransitionInvalid() {
        // INSPECTED is not in CANCELLABLE_STATUSES and not TERMINAL
        Asn asn = asn(AsnStatus.INSPECTED);
        assertThatThrownBy(() -> asn.cancel("reason", NOW, ACTOR))
                .isInstanceOf(StateTransitionInvalidException.class);
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private static Asn asn(AsnStatus status) {
        return new Asn(
                UUID.randomUUID(), "ASN-TEST-001", AsnSource.MANUAL,
                UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.of(2026, 5, 1), null,
                status, 0L,
                NOW, ACTOR, NOW, ACTOR,
                List.of());
    }
}
