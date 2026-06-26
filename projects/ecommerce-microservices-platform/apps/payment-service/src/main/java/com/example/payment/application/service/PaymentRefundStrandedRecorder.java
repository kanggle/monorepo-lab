package com.example.payment.application.service;

import com.example.payment.application.event.PaymentRefundStrandedEvent;
import com.example.payment.application.port.out.PaymentEventPublisher;
import com.example.payment.domain.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Durable money-safety escalation recorder (TASK-BE-437).
 *
 * <p>When {@link PaymentConfirmService#confirm} captures funds for a concurrently
 * cancelled order and then <b>fails to reverse the capture at the PG</b>, the
 * captured customer funds are stranded. This recorder writes a
 * {@code PaymentRefundStranded} escalation to the transactional outbox so the
 * loss is non-silent and operator-recoverable.
 *
 * <p><b>Separate bean / {@code REQUIRES_NEW}.</b> The {@link #record} method runs
 * in its own {@code @Transactional(REQUIRES_NEW)} boundary so the outbox row
 * commits independently of the {@code confirm()} transaction — which rolls back
 * when {@code confirm()} re-throws {@code PaymentAlreadyCompletedException} to
 * reject the now-cancelled payment. It MUST live on a separate bean (not a
 * private method on {@code PaymentConfirmService}): a self-invocation would
 * bypass the Spring AOP proxy and silently inherit the rolling-back outer TX,
 * losing the alert (mirrors order-service's {@code OrderStuckRecoveryHandler}
 * REQUIRES_NEW split).
 *
 * <p>F1 safety: the call site never lets an exception from {@code record(...)}
 * mask the captured-funds loss — it is logged + counted there even if the
 * REQUIRES_NEW write itself throws.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentRefundStrandedRecorder {

    private final PaymentEventPublisher paymentEventPublisher;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String orderId, String paymentId, String paymentKey, long amount, String reason) {
        PaymentRefundStrandedEvent event = PaymentRefundStrandedEvent.of(
                paymentId, orderId, paymentKey, amount, reason, TenantContext.currentTenant());
        paymentEventPublisher.publishPaymentRefundStranded(event);
        log.warn("payment_refund_stranded_escalation_recorded orderId={} paymentId={} reason={}",
                orderId, paymentId, reason);
    }
}
