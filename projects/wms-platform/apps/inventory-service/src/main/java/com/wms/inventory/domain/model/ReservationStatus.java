package com.wms.inventory.domain.model;

/**
 * State machine of a {@link Reservation}.
 *
 * <p>Transitions: {@code RESERVED → CONFIRMED} (terminal) or
 * {@code RESERVED → RELEASED} (terminal). Direct mutations bypassing
 * {@code Reservation.confirm()} / {@code Reservation.release()} are forbidden
 * (T4) — the domain methods are the only entry points.
 */
public enum ReservationStatus {
    RESERVED,
    CONFIRMED,
    RELEASED;

    public boolean isTerminal() {
        return this == CONFIRMED || this == RELEASED;
    }
}
