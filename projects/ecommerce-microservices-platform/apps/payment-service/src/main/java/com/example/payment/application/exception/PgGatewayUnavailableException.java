package com.example.payment.application.exception;

/**
 * Thrown when the upstream Payment Gateway (Toss Payments) is unreachable
 * after Resilience4j retry / circuit-breaker / bulkhead exhaustion.
 *
 * <p><b>Distinct from {@link PgConfirmFailedException}</b> — that exception
 * means the PG processed our request and rejected it (4xx); this exception
 * means the PG did not give us a definitive answer (5xx after retries,
 * timeout, circuit open, bulkhead saturated). Operator playbook differs:
 * a {@code PG_GATEWAY_UNAVAILABLE} response is a transport-level issue
 * (PG vendor outage, network), whereas {@code PG_CONFIRM_FAILED} is a
 * business-level rejection (e.g. duplicate confirmation, invalid card).
 *
 * <p>Caller policy: {@link com.example.payment.application.service.PaymentConfirmService}
 * and {@link com.example.payment.application.service.PaymentRefundService}
 * MUST NOT transition the payment row to {@code FAILED} on this exception —
 * the actual PG-side state is unknown, and locking the row would prevent
 * legitimate retries.
 *
 * <p>Filed by TASK-BE-139 per
 * {@code docs/adr/ADR-MONO-005-saga-timeout-escalation-dead-letter-policy.md}
 * § D4 Category B sub-rules.
 */
public class PgGatewayUnavailableException extends RuntimeException {
    public PgGatewayUnavailableException(String message) {
        super("PG 게이트웨이 도달 불가: " + message);
    }

    public PgGatewayUnavailableException(String message, Throwable cause) {
        super("PG 게이트웨이 도달 불가: " + message, cause);
    }
}
