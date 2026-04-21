package com.example.payment.application.port.out;

import com.example.payment.application.event.PaymentCompletedEvent;
import com.example.payment.application.event.PaymentRefundedEvent;

public interface PaymentEventPublisher {

    void publishPaymentCompleted(PaymentCompletedEvent event);

    void publishPaymentRefunded(PaymentRefundedEvent event);
}
