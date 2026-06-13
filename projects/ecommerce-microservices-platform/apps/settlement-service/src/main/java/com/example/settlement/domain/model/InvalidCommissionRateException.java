package com.example.settlement.domain.model;

/**
 * Thrown when a commission rate is set outside {@code [0, 10000]} bps. Surfaces as
 * HTTP 422 {@code COMMISSION_RATE_INVALID} on {@code PUT /commission-rates/{sellerId}}
 * (AC-4). A domain exception (no framework types) so the invariant is enforced
 * inside the value object regardless of caller.
 */
public class InvalidCommissionRateException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidCommissionRateException(int rateBps) {
        super("commission rateBps must be within [" + CommissionRate.MIN_BPS + ", "
                + CommissionRate.MAX_BPS + "] but was " + rateBps);
    }
}
