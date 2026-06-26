package com.example.payment.application.port.out;

import com.example.payment.application.event.PaymentCompletedEvent;
import com.example.payment.application.event.PaymentRefundStrandedEvent;
import com.example.payment.application.event.PaymentRefundedEvent;

public interface PaymentEventPublisher {

    void publishPaymentCompleted(PaymentCompletedEvent event);

    void publishPaymentRefunded(PaymentRefundedEvent event);

    /**
     * Durable money-safety escalation (TASK-BE-437): the confirm() post-capture
     * auto-refund failed at the PG, leaving captured funds stranded. Written to
     * the outbox so an operator/alert subscriber is notified.
     */
    void publishPaymentRefundStranded(PaymentRefundStrandedEvent event);
}
