package com.wms.inventory.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wms.inventory.domain.exception.StateTransitionInvalidException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReservationTest {

    private static final Instant NOW = Instant.parse("2026-04-25T10:00:00Z");
    private static final Instant LATER = NOW.plusSeconds(60);
    private static final UUID WAREHOUSE = UUID.randomUUID();

    @Test
    void freshReservationStartsInReservedStatus() {
        Reservation r = sample();
        assertThat(r.status()).isEqualTo(ReservationStatus.RESERVED);
        assertThat(r.confirmedAt()).isNull();
        assertThat(r.releasedAt()).isNull();
        assertThat(r.releasedReason()).isNull();
    }

    @Test
    void confirmTransitionsReservedToConfirmedAndStampsAuditFields() {
        Reservation r = sample();
        r.confirm(LATER, "user-1");
        assertThat(r.status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(r.confirmedAt()).isEqualTo(LATER);
        assertThat(r.updatedBy()).isEqualTo("user-1");
    }

    @Test
    void releaseTransitionsReservedToReleasedAndCarriesReason() {
        Reservation r = sample();
        r.release(ReleasedReason.CANCELLED, LATER, "user-1");
        assertThat(r.status()).isEqualTo(ReservationStatus.RELEASED);
        assertThat(r.releasedReason()).isEqualTo(ReleasedReason.CANCELLED);
        assertThat(r.releasedAt()).isEqualTo(LATER);
    }

    @Test
    void confirmTwiceThrowsStateTransitionInvalid() {
        Reservation r = sample();
        r.confirm(LATER, "u");
        assertThatThrownBy(() -> r.confirm(LATER, "u"))
                .isInstanceOf(StateTransitionInvalidException.class);
    }

    @Test
    void releaseAfterConfirmThrowsStateTransitionInvalid() {
        Reservation r = sample();
        r.confirm(LATER, "u");
        assertThatThrownBy(() -> r.release(ReleasedReason.MANUAL, LATER, "u"))
                .isInstanceOf(StateTransitionInvalidException.class);
    }

    @Test
    void confirmAfterReleaseThrowsStateTransitionInvalid() {
        Reservation r = sample();
        r.release(ReleasedReason.CANCELLED, LATER, "u");
        assertThatThrownBy(() -> r.confirm(LATER, "u"))
                .isInstanceOf(StateTransitionInvalidException.class);
    }

    @Test
    void expiresAtMustBeAfterCreatedAt() {
        UUID id = UUID.randomUUID();
        UUID picking = UUID.randomUUID();
        List<ReservationLine> lines = List.of(sampleLine(id));
        assertThatThrownBy(() -> Reservation.create(
                id, picking, WAREHOUSE, lines, NOW, NOW, "u"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Reservation sample() {
        UUID id = UUID.randomUUID();
        return Reservation.create(
                id, UUID.randomUUID(), WAREHOUSE,
                List.of(sampleLine(id)),
                LATER, NOW, "u");
    }

    private static ReservationLine sampleLine(UUID reservationId) {
        return new ReservationLine(
                UUID.randomUUID(), reservationId, UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), null, 5);
    }
}
