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
}
