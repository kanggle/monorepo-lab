package com.example.payment.application.port.out;

import com.example.payment.application.event.PaymentCompletedEvent;
import com.example.payment.application.event.PaymentRefundStrandedEvent;
import com.example.payment.application.event.PaymentRefundUnresolvedEvent;
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

    /**
     * Terminal money-safety escalation (TASK-BE-438): the stranded-refund sweeper could not
     * auto-heal a stranding (attempt cap exhausted or a definitive PG rejection) — the record is
     * now {@code UNRESOLVED} and an operator must act. Written to the outbox, co-committed with
     * the terminal status transition in the reconciler's {@code REQUIRES_NEW} boundary.
     */
    void publishPaymentRefundUnresolved(PaymentRefundUnresolvedEvent event);
}
