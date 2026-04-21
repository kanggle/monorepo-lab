package com.example.payment.application.port.out;

public record PaymentGatewayConfirmResult(String paymentMethod, String receiptUrl) {
}
