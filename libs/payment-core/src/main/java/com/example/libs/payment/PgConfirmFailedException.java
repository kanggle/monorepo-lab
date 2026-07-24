package com.example.libs.payment;

/**
 * Permanent PG-side failure — the gateway processed the request and definitively rejected it
 * (typically a 4xx: duplicate confirmation, invalid card, already-cancelled). No retry will
 * help.
 *
 * <p><b>Distinct from {@link PgGatewayUnavailableException}</b> — that means the PG did not
 * give a definitive answer (transport failure / resilience exhaustion). The caller's recovery
 * policy differs: a permanent failure is a business-level rejection, whereas an unavailable
 * gateway is a transport-level issue the caller must treat as retryable.
 */
public class PgConfirmFailedException extends RuntimeException {
    public PgConfirmFailedException(String message) {
        super("PG 결제 승인 실패: " + message);
    }

    public PgConfirmFailedException(String message, Throwable cause) {
        super("PG 결제 승인 실패: " + message, cause);
    }
}
