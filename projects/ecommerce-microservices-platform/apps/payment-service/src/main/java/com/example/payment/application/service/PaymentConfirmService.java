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

    @Transactional
    public PaymentConfirmResult confirm(String userId, String paymentKey, String orderId, long amount) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new PaymentNotFoundException(orderId));

        if (!payment.isOwnedBy(userId)) {
            throw new UnauthorizedPaymentAccessException();
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
}
