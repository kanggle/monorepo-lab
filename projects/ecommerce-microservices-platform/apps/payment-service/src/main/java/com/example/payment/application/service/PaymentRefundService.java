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
