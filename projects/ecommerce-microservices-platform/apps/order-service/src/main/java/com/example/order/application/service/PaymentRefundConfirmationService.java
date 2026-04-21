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
public class PaymentRefundConfirmationService {

    private final OrderRepository orderRepository;
    private final Clock clock;

    @Transactional
    public void markRefunded(String orderId, Instant refundedAt) {
        Order order = orderRepository.findById(orderId)
                .orElse(null);

        if (order == null) {
            log.warn("PaymentRefunded event received but order not found: orderId={}", orderId);
            return;
        }

        if (order.isRefunded()) {
            log.info("Order already refunded: orderId={}", orderId);
            return;
        }

        try {
            order.markRefunded(refundedAt, clock);
            orderRepository.save(order);
            log.info("Order refund completed: orderId={}", orderId);
        } catch (InvalidOrderException e) {
            log.warn("Cannot mark refunded — invalid order state: orderId={}, reason={}", orderId, e.getMessage());
        }
    }
}
