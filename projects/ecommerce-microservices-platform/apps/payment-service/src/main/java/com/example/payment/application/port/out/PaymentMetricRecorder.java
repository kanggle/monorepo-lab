package com.example.payment.application.port.out;

public interface PaymentMetricRecorder {

    void incrementPaymentCreated();

    void incrementPaymentCompleted();

    void incrementPaymentRefunded();

    void addPaymentAmount(long amount);

    void incrementEventPublishFailure(String eventType);
}
