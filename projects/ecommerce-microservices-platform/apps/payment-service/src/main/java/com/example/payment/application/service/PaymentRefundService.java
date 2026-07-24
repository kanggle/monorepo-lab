package com.example.payment.application.service;

import com.example.payment.application.event.PaymentRefundedEvent;
import com.example.payment.application.exception.IdempotencyKeyRequiredException;
import com.example.payment.application.exception.IdempotencyKeyConflictException;
import com.example.payment.application.exception.UnauthorizedPaymentAccessException;
import com.example.payment.domain.exception.PaymentNotFoundException;
import com.example.libs.payment.RefundablePaymentGateway;
import com.example.payment.application.port.out.PaymentEventPublisher;
import com.example.payment.application.port.out.PaymentMetricRecorder;
import com.example.payment.application.port.out.RefundRequestRepository;
import com.example.payment.domain.model.Payment;
import com.example.payment.domain.model.PaymentStatus;
import com.example.payment.domain.model.RefundRequest;
import com.example.payment.application.port.out.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentRefundService {

    private final PaymentRepository paymentRepository;
    private final PaymentEventPublisher paymentEventPublisher;
    private final PaymentMetricRecorder paymentMetricRecorder;
    private final RefundablePaymentGateway paymentGateway;
    /** Idempotency store for the HTTP partial-refund path (TASK-BE-535). */
    private final RefundRequestRepository refundRequestRepository;
    private final Clock clock;

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
            paymentGateway.refund(payment.getPaymentKey(), "Order cancelled");
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
     * <h2>Idempotency (TASK-BE-535)</h2>
     * {@link Payment#refund(long)} <b>accumulates</b>, so a duplicated request on this path
     * is a genuine double payout — and it is byte-identical to a <em>legitimate</em> second
     * partial refund, so no server-side natural key can separate them. A client
     * {@code idempotencyKey} scoped to the payment does (ADR-002 Decision-3):
     *
     * <ul>
     *   <li><b>Absent / blank key → {@link IdempotencyKeyRequiredException}</b> (400). Refused,
     *       not defaulted: this is a funds-out path, and a keyless request can only be served
     *       non-idempotently.</li>
     *   <li><b>Same key, same amount → replay.</b> Returns the payment's current state without
     *       accumulating again, without re-calling the PG, and without re-publishing
     *       {@code PaymentRefunded} (AC-1).</li>
     *   <li><b>Same key, different amount → {@link IdempotencyKeyConflictException}</b> (409). The
     *       key is bound to the first request's amount; it is never silently replayed for
     *       another.</li>
     *   <li><b>Different key, same payment → proceeds.</b> That is a real second partial refund
     *       and must keep working (AC-2) — the regression a naive "reject any second refund"
     *       guard would introduce.</li>
     * </ul>
     *
     * <p><b>Concurrency (AC-5).</b> The arbiter is the {@code UNIQUE (payment_id,
     * idempotency_key)} index, not the {@link RefundRequestRepository#find} lookup above it:
     * two simultaneous duplicates may both miss the read, but only one
     * {@link RefundRequestRepository#insert} can commit. The loser gets a
     * {@code DataIntegrityViolationException} → 409, having performed no refund — the insert
     * is deliberately ordered <b>before</b> the PG call and before the payment save, so a
     * race loser can never reach the money movement. Same shape as
     * {@code OrderPlacementService.placeOrder}.
     *
     * <p><b>Fail-closed (F3).</b> The dedupe store is this service's own Postgres inside this
     * same transaction — not a separate Redis/lock store that could be unavailable while
     * refunds keep flowing. There is no degraded mode where the guard becomes fail-open: if
     * the DB is unreachable the whole refund fails and no money moves. (The shared wms
     * idempotency filter is fail-open for availability; that calculus does not transfer to a
     * funds-out endpoint.)
     *
     * <p><b>Retention.</b> None — records are kept indefinitely. A record expiring before the
     * caller's retry window closes would re-open the double-payout hole.
     *
     * <p>Unaffected: the event-driven full-refund path
     * ({@link #refundPayment(String)} / {@link #handleOrderCancelled(String)}) needs no key —
     * {@code Payment.refund()} early-returns when already REFUNDED, so it is already
     * idempotent on payment state (AC-3).
     *
     * @return the refreshed payment after the refund (or the unchanged payment on replay).
     */
    @Transactional
    public Payment refundPayment(String paymentId, String requesterUserId, long amount,
                                 String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IdempotencyKeyRequiredException(
                    "Idempotency-Key 헤더는 부분 환불 요청에 필수입니다");
        }

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
        if (!payment.isOwnedBy(requesterUserId)) {
            throw new UnauthorizedPaymentAccessException();
        }

        Optional<RefundRequest> replayed = refundRequestRepository.find(paymentId, idempotencyKey);
        if (replayed.isPresent()) {
            if (!replayed.get().matchesAmount(amount)) {
                throw new IdempotencyKeyConflictException(
                        "동일한 Idempotency-Key 가 다른 환불 금액으로 재사용되었습니다: paymentId="
                                + paymentId + ", 최초=" + replayed.get().getAmount()
                                + ", 요청=" + amount);
            }
            // Replay: the refund was already performed under this key. Do NOT accumulate,
            // do NOT re-call the PG, do NOT re-publish. Return the current state.
            log.info("Idempotent partial-refund replay: paymentId={}, amount={}, totalRefunded={}",
                    paymentId, amount, payment.getRefundedAmount());
            return payment;
        }

        // Claim the key BEFORE any money moves. A concurrent duplicate that also missed the
        // lookup above loses this insert and never reaches the PG call below.
        try {
            refundRequestRepository.insert(
                    RefundRequest.of(paymentId, idempotencyKey, amount, clock.instant()));
        } catch (DataIntegrityViolationException e) {
            log.info("Concurrent duplicate partial refund blocked by unique constraint: "
                    + "paymentId={}, idempotencyKey={}", paymentId, idempotencyKey);
            throw new IdempotencyKeyConflictException(
                    "동일한 Idempotency-Key 의 환불 요청이 이미 처리 중이거나 처리되었습니다: paymentId="
                            + paymentId, e);
        }

        // Domain validates: 0 < amount ≤ remaining, status COMPLETED|PARTIALLY_REFUNDED.
        payment.refund(amount);

        if (payment.getPaymentKey() != null) {
            paymentGateway.refund(payment.getPaymentKey(), "Partial refund", amount);
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
