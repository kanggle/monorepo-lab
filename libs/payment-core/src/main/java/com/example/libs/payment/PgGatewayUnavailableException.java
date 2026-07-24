package com.example.libs.payment;

/**
 * Transient PG failure — the gateway did not give a definitive answer after resilience
 * exhaustion (5xx after retries, timeout, circuit open, bulkhead saturated).
 *
 * <p><b>Distinct from {@link PgConfirmFailedException}</b> — that means the PG processed the
 * request and rejected it (permanent). This exception means the PG-side state is <b>unknown</b>.
 * Caller policy: the caller MUST NOT lock a payment into a terminal {@code FAILED} state on this
 * exception (the actual PG-side outcome is undetermined, and an idempotent retry is expected);
 * it must never infer resolution from it either (a status read that fails this way is treated as
 * transient, not as "already cancelled").
 */
public class PgGatewayUnavailableException extends RuntimeException {
    public PgGatewayUnavailableException(String message) {
        super("PG 게이트웨이 도달 불가: " + message);
    }

    public PgGatewayUnavailableException(String message, Throwable cause) {
        super("PG 게이트웨이 도달 불가: " + message, cause);
    }
}
