package com.example.payment.application.port.out;

public interface PaymentGatewayPort {

    PaymentGatewayConfirmResult confirmPayment(String paymentKey, String orderId, long amount);

    void cancelPayment(String paymentKey, String cancelReason);
}
