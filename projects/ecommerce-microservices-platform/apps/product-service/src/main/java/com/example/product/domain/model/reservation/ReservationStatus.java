package com.example.product.domain.model.reservation;

/**
 * Lifecycle of a {@link StockReservation} (TASK-BE-428, payment-driven reservation saga).
 *
 * <ul>
 *   <li>{@code NEW} — created but not yet reserved (waiting for both the OrderPlaced lines
 *       AND the PaymentCompleted signal to converge; the two topics have no ordering
 *       guarantee).</li>
 *   <li>{@code RESERVED} — all-or-nothing reserve succeeded; stock was decremented for every
 *       line and {@code StockChanged(ORDER_RESERVED)} emitted per line.</li>
 *   <li>{@code BACKORDERED} — at least one line was short at reserve time; NO stock was
 *       decremented and the order is held for a later FIFO re-reservation on restock.</li>
 *   <li>{@code RELEASED} — terminal; the order was cancelled. If it was {@code RESERVED} the
 *       decremented stock was restored; from {@code NEW}/{@code BACKORDERED} no stock change.</li>
 * </ul>
 */
public enum ReservationStatus {
    NEW,
    RESERVED,
    BACKORDERED,
    RELEASED
}
