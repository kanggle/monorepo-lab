package com.example.settlement.domain.period;

/**
 * Thrown when {@code close} is called on an already-CLOSED period. Surfaces as HTTP
 * 409 {@code PERIOD_ALREADY_CLOSED} on {@code POST /periods/{id}/close} (AC-7). The
 * close is NOT idempotent — re-closing must not create duplicate payout rows or emit
 * a second {@code settlement.period.closed.v1}.
 */
public class PeriodAlreadyClosedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PeriodAlreadyClosedException(String message) {
        super(message);
    }
}
