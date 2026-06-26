package com.example.payment.application.port.out;

public interface PaymentGatewayPort {

    PaymentGatewayConfirmResult confirmPayment(String paymentKey, String orderId, long amount);

    /** Full cancel of the payment at the PG (the OrderCancelled path). */
    void cancelPayment(String paymentKey, String cancelReason);

    /**
     * Partial (or full) cancel of {@code cancelAmount} minor units at the PG (the
     * HTTP partial-refund path — sends Toss {@code cancelAmount}).
     */
    void cancelPayment(String paymentKey, String cancelReason, long cancelAmount);

    /**
     * Read-only PG state lookup (Toss {@code GET /v1/payments/{paymentKey}}) used by the
     * stranded-refund sweeper's double-refund guard (TASK-BE-438). A transient stranding may
     * have actually cancelled at the PG, so the sweeper checks this <b>before</b> re-issuing a
     * cancel. Resilience4j-wrapped like the other PG calls; any failure surfaces as
     * {@code PgGatewayUnavailableException} / {@code PgConfirmFailedException} and the sweeper
     * treats it as transient (never infers resolution from an error).
     */
    PaymentGatewayStatus fetchStatus(String paymentKey);
}
