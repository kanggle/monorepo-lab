package com.example.payment.application.service;

import com.example.payment.application.exception.UnauthorizedPaymentAccessException;
import com.example.payment.application.port.out.PaymentMetricRecorder;
import com.example.payment.application.port.out.PaymentRepository;
import com.example.payment.domain.model.Payment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

        Payment payment = Payment.create(orderId, userId, amount);
        paymentMetricRecorder.incrementPaymentCreated();
        paymentRepository.save(payment);

        log.info("Payment created (PENDING): paymentId={}, orderId={}", payment.getPaymentId(), orderId);
    }
}
