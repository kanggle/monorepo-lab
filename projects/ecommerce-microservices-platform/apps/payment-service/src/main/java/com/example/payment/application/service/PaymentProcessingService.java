package com.example.payment.application.service;

import com.example.payment.application.exception.UnauthorizedPaymentAccessException;
import com.example.payment.application.port.out.PaymentMetricRecorder;
import com.example.payment.application.port.out.PaymentRepository;
import com.example.payment.domain.exception.PaymentNotFoundException;
import com.example.payment.domain.model.Payment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentProcessingService {

    private final PaymentRepository paymentRepository;
    private final PaymentMetricRecorder paymentMetricRecorder;

    /**
     * Create a PENDING payment for the given order on behalf of the caller.
     *
     * <p>Ownership semantics (see TASK-BE-128):
     * <ul>
     *   <li>If a payment already exists for this {@code orderId} and its {@code userId}
     *   does not match the caller, throw {@link UnauthorizedPaymentAccessException} (403).
     *   This blocks an attacker who knows another user's {@code orderId} from binding
     *   their own user identity to that order's payment.</li>
     *   <li>If a payment already exists and is owned by the caller, the request is
     *   treated as idempotent (no-op).</li>
     *   <li>For the very first {@code processPayment} call against a brand-new
     *   {@code orderId}, no payment record exists yet, so ownership cannot be
     *   cross-checked at this layer. The order-service is the source of truth for
     *   {@code orderId} → {@code userId} ownership; introducing a synchronous HTTP
     *   client to order-service is out of scope for this task. In practice the
     *   first {@code Payment} is created by the trusted {@code OrderPlacedEventConsumer}
     *   path using the event's {@code userId}, so the REST entry point becomes a
     *   no-op or a 403 by the time an attacker tries to hijack it.</li>
     * </ul>
     *
     * <p><b>Tenant axis (TASK-BE-543 AC-1).</b> {@code orderId} is globally unique
     * (single generation site, {@code UUID.randomUUID()}, in {@code Order.create} —
     * no tenant-partitioned scheme), so the {@code payments.order_id UNIQUE} constraint
     * is correctly global (V1). The own-tenant lookup above only sees a same-tenant row,
     * so a caller in a DIFFERENT tenant than the row's true owner would fall through to
     * the create branch below and hit that global constraint at insert time — surfacing
     * as a 409 that also leaks "this orderId already exists somewhere" across the tenant
     * boundary (M3 requires masking cross-tenant existence, not a bare 409). Before
     * inserting, a global existence check rejects that case with 404 instead — the same
     * status this endpoint already returns for "no such payment", so cross-tenant and
     * genuinely-nonexistent orders are indistinguishable to the caller.
     */
    @Transactional
    public void processPayment(String orderId, String userId, long amount) {
        var existing = paymentRepository.findByOrderId(orderId);
        if (existing.isPresent()) {
            Payment payment = existing.get();
            if (!payment.isOwnedBy(userId)) {
                log.warn("Payment ownership mismatch: orderId={}, requestUserId={}, paymentUserId={}",
                        orderId, userId, payment.getUserId());
                throw new UnauthorizedPaymentAccessException();
            }
            log.info("Payment already exists for orderId={}, skipping", orderId);
            return;
        }

        if (paymentRepository.existsByOrderIdAcrossTenants(orderId)) {
            // orderId already has a payment under a different tenant. Reject before the
            // insert would otherwise hit the global order_id UNIQUE constraint — masking
            // cross-tenant existence as PAYMENT_NOT_FOUND (404) rather than a 409/500 that
            // confirms something exists (TASK-BE-543 AC-1, M3).
            log.warn("processPayment: orderId={} already exists under a different tenant — "
                    + "rejecting as not found", orderId);
            throw new PaymentNotFoundException(orderId);
        }

        Payment payment = Payment.create(orderId, userId, amount);
        try {
            // saveAndFlush, not save: Payment has an assigned @Id, so a plain save() would
            // defer this INSERT to the commit-time flush — past this catch and past the
            // controller — and the catch below would be dead code (TASK-BE-541).
            paymentRepository.saveAndFlush(payment);
        } catch (DataIntegrityViolationException e) {
            // The concurrent twin of the precheck above: two cross-tenant requests can both
            // pass existsByOrderIdAcrossTenants before either commits, so the global
            // payments.order_id UNIQUE constraint is the real arbiter. Translate it to the
            // same 404 the sequential path returns — otherwise the loser gets the 409 that
            // the precheck exists to avoid, and cross-tenant existence leaks after all (M3).
            log.warn("processPayment: concurrent insert lost the race on orderId={} — "
                    + "rejecting as not found", orderId, e);
            throw new PaymentNotFoundException(orderId);
        }
        paymentMetricRecorder.incrementPaymentCreated();

        log.info("Payment created (PENDING): paymentId={}, orderId={}", payment.getPaymentId(), orderId);
    }
}
