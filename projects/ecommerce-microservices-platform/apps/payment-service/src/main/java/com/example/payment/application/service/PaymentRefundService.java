package com.example.payment.application.service;

import com.example.payment.application.event.PaymentRefundedEvent;
import com.example.payment.application.exception.UnauthorizedPaymentAccessException;
import com.example.payment.domain.exception.PaymentNotFoundException;
import com.example.payment.application.port.out.PaymentEventPublisher;
import com.example.payment.application.port.out.PaymentGatewayPort;
import com.example.payment.application.port.out.PaymentMetricRecorder;
import com.example.payment.domain.model.Payment;
import com.example.payment.domain.model.PaymentStatus;
import com.example.payment.application.port.out.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentRefundService {

    private final PaymentRepository paymentRepository;
    private final PaymentEventPublisher paymentEventPublisher;
    private final PaymentMetricRecorder paymentMetricRecorder;
    private final PaymentGatewayPort paymentGateway;

    /**
     * Money-safe handler for an {@code OrderCancelled} event (TASK-BE-435 §B). Branches on the
     * current {@code Payment} row state so both event orderings are safe:
     * <ul>
     *   <li><b>COMPLETED</b> (already captured) → full {@link #refundPayment(String)} auto-refund.</li>
     *   <li><b>PENDING</b> (not yet confirmed) → {@link #voidPayment(String)} so any later
     *       {@code confirm()} is rejected (no capture).</li>
     *   <li>Already terminal (VOIDED/REFUNDED/FAILED) or absent → no-op (idempotent).</li>
     * </ul>
     * The branch is independent of {@code cancelReason}: an operator cancel and a
     * PAYMENT_TIMEOUT stuck-cancel both require money safety. Idempotency is provided by the
     * delegated methods (refund gates on already-REFUNDED; void gates on terminal state), so a
     * duplicate {@code OrderCancelled} is a no-op (AC-7).
     *
     * <p>This method is the proxy entry point for the {@code OrderCancelled} consumer
     * ({@code OrderCancelledEventConsumer.handle}), so it carries the {@code @Transactional}
     * boundary for the whole branch (TASK-BE-440). The delegated {@link #refundPayment(String)} /
     * {@link #voidPayment(String)} are reached via self-invocation, which bypasses the Spring AOP
     * proxy — their own {@code @Transactional} therefore does NOT start a transaction on the
     * consumer thread; without the annotation here the {@code save}/{@code persist} would run with
     * no active transaction ({@code InvalidDataAccessApiUsageException: No EntityManager with actual
     * transaction available}). With this boundary the advisory dispatch read and the delegate's
     * read-modify-write share ONE {@code REQUIRED} transaction (mirroring
     * {@code PaymentConfirmService.confirm()}'s single boundary). The delegates' own
     * {@code @Transactional} simply joins this transaction (and still works on the HTTP
     * partial-refund entry point, which enters them through the proxy).
     *
     * <p>A concurrent PENDING→COMPLETED flip between the dispatch read here and the delegate is the
     * genuinely-concurrent interleave handled belt-and-suspenders by
     * {@code PaymentConfirmService.confirm()} (which re-checks VOIDED under the row and auto-refunds
     * a just-captured amount if the order was cancelled) — see that class. The dispatch read here is
     * advisory.
     */
    @Transactional
    public void handleOrderCancelled(String orderId) {
        Optional<Payment> paymentOpt = paymentRepository.findByOrderId(orderId);
        if (paymentOpt.isEmpty()) {
            log.info("No payment found for orderId={}, OrderCancelled is a no-op", orderId);
            return;
        }
        Payment payment = paymentOpt.get();
        switch (payment.getStatus()) {
            case COMPLETED, PARTIALLY_REFUNDED ->
                // Captured (possibly partially refunded already) — refund the remainder.
                    refundPayment(orderId);
            case PENDING ->
                // Never captured — void so a late confirm cannot capture.
                    voidPayment(orderId);
            default ->
                // VOIDED / REFUNDED / FAILED — already money-safe terminal. No-op.
                    log.info("OrderCancelled for orderId={} with terminal payment status {}, no-op",
                            orderId, payment.getStatus());
        }
    }

    @Transactional
    public void refundPayment(String orderId) {
        Optional<Payment> paymentOpt = paymentRepository.findByOrderId(orderId);
        if (paymentOpt.isEmpty()) {
            log.info("No payment found for orderId={}, skipping refund", orderId);
            return;
        }

        Payment payment = paymentOpt.get();
        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            log.warn("Duplicate refund attempt for orderId={}, paymentId={}", orderId, payment.getPaymentId());
            return;
        }

        // The full path refunds the remaining refundable (closes the payment out).
        long thisRefund = payment.getRemainingRefundable();

        if (payment.getPaymentKey() != null) {
            // Adapter throws PgConfirmFailedException on 4xx and
            // PgGatewayUnavailableException on 5xx-exhaustion / circuit open
            // (ADR-MONO-005 § D4). Both propagate uncaught — @Transactional
            // rolls back, payment row stays in its prior state, caller's
            // retry/DLT mechanism re-drives. No state advance on transport
            // failure (PG actual state unknown).
            paymentGateway.cancelPayment(payment.getPaymentKey(), "Order cancelled");
        } else {
            log.info("No paymentKey for orderId={}, skipping PG cancel", orderId);
        }

        payment.refund();
        paymentRepository.save(payment);
        paymentMetricRecorder.incrementPaymentRefunded();

        paymentEventPublisher.publishPaymentRefunded(PaymentRefundedEvent.from(payment, thisRefund));

        log.info("Payment refunded: paymentId={}, orderId={}", payment.getPaymentId(), orderId);
    }

    /**
     * Void a PENDING payment whose order was cancelled before capture (TASK-BE-435, the
     * {@code OrderCancelled} → PENDING branch). Transitions {@code PENDING → VOIDED} so any
     * later {@code confirm()} is rejected and no funds are ever captured.
     *
     * <p>No PG money movement occurred (the payment was never captured), so there is NO PG
     * cancel call and NO {@code PaymentRefunded} event — there is nothing to refund. The
     * void is purely an internal terminal-state guard.
     *
     * <p>Idempotent: a no-op when the payment is absent or already terminal
     * ({@code VOIDED}/{@code REFUNDED}/{@code FAILED}), so a duplicate {@code OrderCancelled}
     * does not double-transition. A {@code COMPLETED} payment is NOT voided here — that case
     * is the consumer's COMPLETED→{@link #refundPayment(String)} branch.
     */
    @Transactional
    public void voidPayment(String orderId) {
        Optional<Payment> paymentOpt = paymentRepository.findByOrderId(orderId);
        if (paymentOpt.isEmpty()) {
            log.info("No payment found for orderId={}, skipping void", orderId);
            return;
        }

        Payment payment = paymentOpt.get();
        boolean transitioned = payment.voidForOrderCancelled();
        if (!transitioned) {
            log.info("Payment for orderId={} already terminal ({}), void is a no-op",
                    orderId, payment.getStatus());
            return;
        }

        paymentRepository.save(payment);
        log.info("Payment voided (order cancelled before capture): paymentId={}, orderId={}",
                payment.getPaymentId(), orderId);
    }

    /**
     * Partial (or full) refund of {@code amount} minor units for one payment (the HTTP
     * path). The caller must be the payment owner. Validates the amount against the
     * remaining refundable (domain {@code refund(amount)} rejects over-refund), cancels
     * the amount at the PG, accumulates {@code refundedAmount}, and publishes a
     * {@code PaymentRefunded} carrying this refund's amount + cumulative + fully-refunded.
     *
     * @return the refreshed payment after the refund.
     */
    @Transactional
    public Payment refundPayment(String paymentId, String requesterUserId, long amount) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
        if (!payment.isOwnedBy(requesterUserId)) {
            throw new UnauthorizedPaymentAccessException();
        }

        // Domain validates: 0 < amount ≤ remaining, status COMPLETED|PARTIALLY_REFUNDED.
        payment.refund(amount);

        if (payment.getPaymentKey() != null) {
            paymentGateway.cancelPayment(payment.getPaymentKey(), "Partial refund", amount);
        } else {
            log.info("No paymentKey for paymentId={}, skipping PG partial cancel", paymentId);
        }

        paymentRepository.save(payment);
        paymentMetricRecorder.incrementPaymentRefunded();
        paymentEventPublisher.publishPaymentRefunded(PaymentRefundedEvent.from(payment, amount));

        log.info("Payment partially refunded: paymentId={}, amount={}, totalRefunded={}, fullyRefunded={}",
                paymentId, amount, payment.getRefundedAmount(), payment.isFullyRefunded());
        return payment;
    }
}
