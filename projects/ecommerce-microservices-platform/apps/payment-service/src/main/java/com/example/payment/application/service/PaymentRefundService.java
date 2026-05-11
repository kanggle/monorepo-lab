package com.example.payment.application.service;

import com.example.payment.application.event.PaymentRefundedEvent;
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

        paymentEventPublisher.publishPaymentRefunded(PaymentRefundedEvent.from(payment));

        log.info("Payment refunded: paymentId={}, orderId={}", payment.getPaymentId(), orderId);
    }
}
