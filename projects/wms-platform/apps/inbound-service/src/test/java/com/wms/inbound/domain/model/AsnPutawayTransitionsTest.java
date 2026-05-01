package com.wms.inbound.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wms.inbound.domain.exception.StateTransitionInvalidException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the three new state transitions added to {@link Asn}:
 * {@code instructPutaway()}, {@code completePutaway()}, {@code close()}.
 */
class AsnPutawayTransitionsTest {

    private static final Instant NOW = Instant.parse("2026-04-29T10:00:00Z");
    private static final String ACTOR = "test-actor";

    @Test
    void instructPutaway_fromInspected_transitionsToInPutaway() {
        Asn asn = asn(AsnStatus.INSPECTED);
        asn.instructPutaway(NOW, ACTOR);
        assertThat(asn.getStatus()).isEqualTo(AsnStatus.IN_PUTAWAY);
    }

    @Test
    void instructPutaway_fromCreated_throwsStateTransitionInvalid() {
        Asn asn = asn(AsnStatus.CREATED);
        assertThatThrownBy(() -> asn.instructPutaway(NOW, ACTOR))
                .isInstanceOf(StateTransitionInvalidException.class);
    }

    @Test
    void completePutaway_fromInPutaway_transitionsToPutawayDone() {
        Asn asn = asn(AsnStatus.IN_PUTAWAY);
        asn.completePutaway(NOW, ACTOR);
        assertThat(asn.getStatus()).isEqualTo(AsnStatus.PUTAWAY_DONE);
    }

    @Test
    void completePutaway_fromInspected_throwsStateTransitionInvalid() {
        Asn asn = asn(AsnStatus.INSPECTED);
        assertThatThrownBy(() -> asn.completePutaway(NOW, ACTOR))
                .isInstanceOf(StateTransitionInvalidException.class);
    }

    @Test
    void close_fromPutawayDone_transitionsToClosed() {
        Asn asn = asn(AsnStatus.PUTAWAY_DONE);
        asn.close(NOW, ACTOR);
        assertThat(asn.getStatus()).isEqualTo(AsnStatus.CLOSED);
    }

    @Test
    void close_fromInPutaway_throwsStateTransitionInvalid() {
        Asn asn = asn(AsnStatus.IN_PUTAWAY);
        assertThatThrownBy(() -> asn.close(NOW, ACTOR))
                .isInstanceOf(StateTransitionInvalidException.class);
    }

    @Test
    void close_fromClosed_throwsStateTransitionInvalid() {
        Asn asn = asn(AsnStatus.CLOSED);
        assertThatThrownBy(() -> asn.close(NOW, ACTOR))
                .isInstanceOf(StateTransitionInvalidException.class);
    }

    private static Asn asn(AsnStatus status) {
        return new Asn(UUID.randomUUID(), "ASN-TEST-001", AsnSource.MANUAL,
                UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.of(2026, 5, 1), null,
                status, 0L, NOW, ACTOR, NOW, ACTOR, List.of());
    }
}
