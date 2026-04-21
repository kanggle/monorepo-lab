package com.example.order.application.service;

import com.example.order.domain.exception.InvalidOrderException;
import com.example.order.domain.model.Order;
import com.example.order.domain.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentConfirmationService {

    private final OrderRepository orderRepository;
    private final Clock clock;

    @Transactional
    public void markPaymentCompleted(String orderId, String paymentId, Instant paidAt) {
        Order order = orderRepository.findById(orderId)
                .orElse(null);

        if (order == null) {
            log.warn("Order not found for PaymentCompleted event: orderId={}", orderId);
            return;
        }

        if (order.isPaymentCompleted()) {
            log.info("Payment already completed for order: orderId={}, paymentId={}", orderId, paymentId);
            return;
        }

        try {
            order.markPaymentCompleted(paymentId, paidAt, clock);
            orderRepository.save(order);
            log.info("Payment completed for order: orderId={}, paymentId={}", orderId, paymentId);
        } catch (InvalidOrderException e) {
            log.warn("Cannot mark payment completed — invalid order state: orderId={}, reason={}", orderId, e.getMessage());
        }
    }
}
