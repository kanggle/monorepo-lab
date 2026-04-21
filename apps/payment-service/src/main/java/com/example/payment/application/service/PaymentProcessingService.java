package com.example.payment.application.service;

import com.example.payment.application.port.out.PaymentMetricRecorder;
import com.example.payment.domain.model.Payment;
import com.example.payment.application.port.out.PaymentRepository;
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

    @Transactional
    public void processPayment(String orderId, String userId, long amount) {
        if (paymentRepository.findByOrderId(orderId).isPresent()) {
            log.info("Payment already exists for orderId={}, skipping", orderId);
            return;
        }

        Payment payment = Payment.create(orderId, userId, amount);
        paymentMetricRecorder.incrementPaymentCreated();
        paymentRepository.save(payment);

        log.info("Payment created (PENDING): paymentId={}, orderId={}", payment.getPaymentId(), orderId);
    }
}
