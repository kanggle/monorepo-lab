package com.example.payment.application.service;

import com.example.payment.application.event.PaymentCompletedEvent;
import com.example.payment.application.exception.AmountMismatchException;
import com.example.payment.application.exception.PaymentAlreadyCompletedException;
import com.example.payment.application.exception.PgConfirmFailedException;
import com.example.payment.application.exception.PgGatewayUnavailableException;
import com.example.payment.application.exception.UnauthorizedPaymentAccessException;
import com.example.payment.application.port.out.PaymentEventPublisher;
import com.example.payment.application.port.out.PaymentGatewayConfirmResult;
import com.example.payment.application.port.out.PaymentGatewayPort;
import com.example.payment.application.port.out.PaymentMetricRecorder;
import com.example.payment.application.port.out.PaymentRepository;
import com.example.payment.domain.exception.PaymentNotFoundException;
import com.example.payment.domain.model.Payment;
import com.example.payment.domain.model.PaymentStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentConfirmService {

    private final PaymentRepository paymentRepository;
    private final PaymentGatewayPort paymentGateway;
    private final PaymentEventPublisher paymentEventPublisher;
    private final PaymentMetricRecorder paymentMetricRecorder;
    private final PaymentRefundStrandedRecorder paymentRefundStrandedRecorder;

    @Transactional
    public PaymentConfirmResult confirm(String userId, String paymentKey, String orderId, long amount) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new PaymentNotFoundException(orderId));

        if (!payment.isOwnedBy(userId)) {
            throw new UnauthorizedPaymentAccessException();
        }
        // TASK-BE-435 belt-and-suspenders, pre-capture guard: if the order was already
        // cancelled before capture, the Payment is VOIDED — reject the confirm so we never
        // call the PG. (VOIDED is also "not PENDING", so the generic guard below would catch
        // it; it is checked explicitly because losing the customer's money on a cancelled
        // order is the money-safety-critical case, and the domain confirm() likewise rejects VOIDED.)
        if (payment.getStatus() == PaymentStatus.VOIDED) {
            throw new PaymentAlreadyCompletedException(orderId);
        }
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new PaymentAlreadyCompletedException(orderId);
        }
        if (payment.getAmount() != amount) {
            throw new AmountMismatchException(payment.getAmount(), amount);
        }

        PaymentGatewayConfirmResult pgResult;
        try {
            pgResult = paymentGateway.confirmPayment(paymentKey, orderId, amount);
        } catch (PgConfirmFailedException e) {
            // PG-side definitive rejection (4xx). Lock the row to FAILED so the
            // user must start a new order — the PG already processed and
            // declined this confirm.
            payment.fail();
            paymentRepository.save(payment);
            throw e;
        } catch (PgGatewayUnavailableException e) {
            // Transport failure (5xx exhaustion / circuit open / timeout —
            // ADR-MONO-005 § D4 Category B). PG actual state is unknown —
            // DO NOT transition to FAILED. Propagate so the @Transactional
            // boundary rolls back and the user can idempotently retry.
            throw e;
        }

        // ── TASK-BE-435 belt-and-suspenders, post-capture auto-refund guard ──────────────
        // Concurrency mechanism: a row re-read after the PG capture detects an OrderCancelled
        // that committed a VOIDED transition AFTER our initial read but DURING the (slow) PG
        // call — the genuinely-concurrent interleave the pre-capture guard cannot see. We just
        // captured money for an order that is now cancelled, so we immediately cancel the
        // just-captured amount at the PG and do NOT advance to COMPLETED (the row stays VOIDED,
        // no PaymentCompleted is published). Funds are never retained.
        //
        // Why a FRESH re-read (TASK-BE-443 money-safety fix): the re-read MUST observe the
        // committed VOIDED, but the pre-capture read above already loaded this row as a MANAGED
        // entity in this transaction's persistence context (L1). A plain findByOrderId would run
        // its SQL yet re-hydrate the matched row through the same session, where Hibernate's
        // managed-entity identity (session-level repeatable-read) returns the STALE PENDING
        // instance and discards the freshly-read VOIDED columns — masking the race regardless of
        // the DB isolation level (READ_COMMITTED governs what the SQL fetches, not whether
        // Hibernate uses it for an already-managed entity, and there is no @Version on the row).
        // findByOrderIdFresh forces an entityManager.refresh so the committed VOIDED is actually
        // seen. Vs. an optimistic @Version: a re-read needs no schema change and is the simplest
        // correct guard. (If the void instead commits AFTER this transaction, the void path is a
        // no-op on a COMPLETED row and the consumer's COMPLETED→refund branch reverses it — also
        // safe.) In the no-race case the refresh is a no-op (row still PENDING) so confirm()
        // proceeds to the success path below byte-for-byte unchanged.
        Payment latest = paymentRepository.findByOrderIdFresh(orderId).orElse(payment);
        if (latest.isVoided()) {
            if (paymentKey != null) {
                try {
                    paymentGateway.cancelPayment(paymentKey, "Order cancelled during confirm");
                } catch (PgGatewayUnavailableException | PgConfirmFailedException e) {
                    // TASK-BE-437 money-safety escalation: the post-capture auto-refund failed at the
                    // PG, so the just-captured customer funds are stranded with no DLT/retry on this
                    // synchronous HTTP path. Durably record + escalate (see escalateStrandedRefund),
                    // then still reject the confirm (order cancelled; never advance VOIDED→COMPLETED).
                    escalateStrandedRefund(orderId, latest, paymentKey, e);
                    throw new PaymentAlreadyCompletedException(orderId);
                }
            }
            paymentMetricRecorder.incrementPaymentRefunded();
            log.warn("Confirm captured funds for an already-cancelled order — auto-cancelled the "
                    + "captured amount at the PG. orderId={}, paymentId={}", orderId, latest.getPaymentId());
            throw new PaymentAlreadyCompletedException(orderId);
        }

        payment.confirm(paymentKey, pgResult.paymentMethod(), pgResult.receiptUrl());
        paymentRepository.save(payment);

        paymentMetricRecorder.incrementPaymentCompleted();
        paymentMetricRecorder.addPaymentAmount(amount);

        paymentEventPublisher.publishPaymentCompleted(PaymentCompletedEvent.from(payment));

        log.info("Payment confirmed: paymentId={}, orderId={}", payment.getPaymentId(), orderId);

        return new PaymentConfirmResult(
                payment.getPaymentId(),
                orderId,
                payment.getStatus().name(),
                pgResult.paymentMethod(),
                pgResult.receiptUrl(),
                payment.getPaidAt()
        );
    }

    /**
     * Durably record + escalate a stranded post-capture refund (TASK-BE-437). The recorder writes
     * in a REQUIRES_NEW boundary (separate bean) so the escalation COMMITS even though the calling
     * confirm() is about to roll back; the money-safety metric is incremented and the loss logged.
     * F1: a failure of the escalation write itself must never mask the captured-funds loss, so the
     * record call is wrapped and its failure logged rather than propagated.
     */
    private void escalateStrandedRefund(String orderId, Payment latest, String paymentKey, RuntimeException cause) {
        String reason = cause.getClass().getSimpleName();
        try {
            paymentRefundStrandedRecorder.record(
                    orderId, latest.getPaymentId(), paymentKey, latest.getAmount(), reason);
        } catch (RuntimeException recordFailure) {
            log.error("payment_refund_stranded_escalation_FAILED — captured funds may be "
                            + "stranded AND the escalation write failed. orderId={}, paymentId={}, "
                            + "paymentKey={}, amount={}, cause={}",
                    orderId, latest.getPaymentId(), paymentKey, latest.getAmount(), reason,
                    recordFailure);
        }
        paymentMetricRecorder.incrementRefundStranded();
        log.error("payment_refund_stranded — confirm() post-capture auto-refund failed at "
                        + "the PG; captured funds stranded pending operator action. orderId={}, "
                        + "paymentId={}, paymentKey={}, amount={}, cause={}",
                orderId, latest.getPaymentId(), paymentKey, latest.getAmount(), reason, cause);
    }
}
