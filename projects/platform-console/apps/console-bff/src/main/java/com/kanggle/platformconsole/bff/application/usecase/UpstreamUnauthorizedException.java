package com.kanggle.platformconsole.bff.application.usecase;

/**
 * Thrown by a composition use case when any outbound leg returns 401 to the BFF.
 *
 * <p>Per {@code console-integration-contract.md} § 2.4.9.1 + § 2.4.4 D3
 * (cross-leg discipline): the tokens are shared across legs from the same
 * inbound request — a 401 on one leg is a 401 for all. The composition route
 * collapses to a composition-level {@code 401 TOKEN_INVALID} (NOT a per-card
 * degrade — auth is not a degrade classification at MVP).
 *
 * <p>Mapped by the inbound web {@code GlobalExceptionHandler} to the standard
 * error envelope.
 */
public class UpstreamUnauthorizedException extends RuntimeException {

    public UpstreamUnauthorizedException(String message) {
        super(message);
    }
}
