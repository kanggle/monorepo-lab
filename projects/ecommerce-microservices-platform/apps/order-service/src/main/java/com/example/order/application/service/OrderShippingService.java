package com.example.order.application.service;

import com.example.order.application.port.OrderMetricsPort;
import com.example.order.domain.exception.InvalidOrderException;
import com.example.order.domain.model.Order;
import com.example.order.domain.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Return-leg tail of the storefront→warehouse loop (ADR-MONO-022 §D7): when
 * shipping-service reports a Shipping record reached {@code SHIPPED}, flip the
 * Order {@code CONFIRMED → SHIPPED}. Idempotent on re-delivery and on an
 * already-SHIPPED order.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderShippingService {

    private final OrderRepository orderRepository;
    private final OrderMetricsPort orderMetrics;
    private final Clock clock;

    @Transactional
    public void markShipped(String orderId) {
        Order order = orderRepository.findByIdAcrossTenants(orderId).orElse(null);
        if (order == null) {
            log.warn("Order not found for ShippingStatusChanged(SHIPPED) event: orderId={}", orderId);
            return;
        }

        try {
            String previousStatus = order.getStatus().name();
            boolean changed = order.ship(clock);
            orderRepository.save(order);
            if (changed) {
                orderMetrics.recordStatusTransition(previousStatus, order.getStatus().name());
                log.info("Order shipped: orderId={}", orderId);
            }
        } catch (InvalidOrderException e) {
            log.warn("Cannot mark order shipped — invalid order state: orderId={}, reason={}",
                    orderId, e.getMessage());
        }
    }
}
