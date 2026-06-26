package com.example.payment.application.service;

import com.example.payment.application.event.PaymentRefundStrandedEvent;
import com.example.payment.application.port.out.PaymentEventPublisher;
import com.example.payment.application.port.out.StrandedRefundRepository;
import com.example.payment.domain.model.StrandedRefund;
import com.example.payment.domain.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

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
 *
 * <p><b>Queryable record (TASK-BE-438).</b> In the SAME {@code REQUIRES_NEW} transaction it also
 * persists a {@link StrandedRefund} row, so the durable obligation the {@code StrandedRefundSweeper}
 * polls and the escalation alert commit atomically across the {@code confirm()} rollback (AC-1).
 * Dedupe (Edge Case "Record dedupe"): a client retrying the same failed confirm must not create a
 * second open row — if an open ({@code STRANDED}) row already exists for the payment, the row write
 * is skipped (the existing obligation stands). The escalation <b>event</b> may still re-emit on the
 * retry; the future alert consumer dedupes on {@code paymentId} / {@code event_id} (documented in
 * the contract).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentRefundStrandedRecorder {

    private final PaymentEventPublisher paymentEventPublisher;
    private final StrandedRefundRepository strandedRefundRepository;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String orderId, String paymentId, String paymentKey, long amount, String reason) {
        PaymentRefundStrandedEvent event = PaymentRefundStrandedEvent.of(
                paymentId, orderId, paymentKey, amount, reason, TenantContext.currentTenant());
        paymentEventPublisher.publishPaymentRefundStranded(event);

        // Durable, queryable obligation for the sweeper — co-committed with the escalation event.
        // Dedupe: only one open obligation per payment_id (the partial unique index is the hard
        // guard; this read-then-insert handles the common sequential client-retry case).
        if (strandedRefundRepository.findOpenByPaymentId(paymentId).isEmpty()) {
            Instant now = Instant.now(clock);
            strandedRefundRepository.save(
                    StrandedRefund.open(paymentId, orderId, paymentKey, amount, reason, now));
        } else {
            log.debug("payment_refund_stranded_record_dedupe — open record already exists, "
                    + "not creating a duplicate. paymentId={}", paymentId);
        }

        log.warn("payment_refund_stranded_escalation_recorded orderId={} paymentId={} reason={}",
                orderId, paymentId, reason);
    }
}
