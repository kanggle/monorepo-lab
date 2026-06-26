package com.example.payment.application.port.out;

public interface PaymentMetricRecorder {

    void incrementPaymentCreated();

    void incrementPaymentCompleted();

    void incrementPaymentRefunded();

    /**
     * Money-safety counter (TASK-BE-437): the confirm() post-capture auto-refund
     * failed at the PG, leaving captured funds stranded pending operator action.
     */
    void incrementRefundStranded();

    void addPaymentAmount(long amount);

    void incrementEventPublishFailure(String eventType);
}
