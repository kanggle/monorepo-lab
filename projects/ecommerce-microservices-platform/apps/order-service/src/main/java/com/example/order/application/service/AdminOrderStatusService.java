package com.example.order.application.service;

import com.example.order.application.dto.AdminOrderStatusChangeResult;
import com.example.order.application.exception.InvalidOrderStatusException;
import com.example.order.domain.exception.OrderNotFoundException;
import com.example.order.domain.model.Order;
import com.example.order.domain.model.OrderStatus;
import com.example.order.domain.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminOrderStatusService {

    private final OrderRepository orderRepository;
    private final Clock clock;

    @Transactional
    public AdminOrderStatusChangeResult changeStatus(String orderId, String targetStatusRaw) {
        OrderStatus targetStatus = parseStatus(targetStatusRaw);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        OrderStatus previousStatus = order.getStatus();

        switch (targetStatus) {
            case CONFIRMED -> order.confirm(clock);
            case CANCELLED -> order.cancel(clock);
            // SHIPPED / DELIVERED are NOT operator-settable on this endpoint. The Order
            // reaches those states solely via the shipping-driven return-leg
            // (ShippingStatusChanged -> OrderShippingService.markShipped/markDelivered,
            // ADR-MONO-022 §D7). Submitting them here falls through to default and is
            // rejected (400 INVALID_ORDER_REQUEST) to prevent order/shipping divergence.
            default -> throw new IllegalArgumentException("Unsupported target status: " + targetStatus);
        }

        orderRepository.save(order);
        log.info("Admin changed order status: orderId={}, {} -> {}", orderId, previousStatus, targetStatus);
        return new AdminOrderStatusChangeResult(order.getOrderId(), order.getStatus().name());
    }

    private static OrderStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidOrderStatusException(raw);
        }
        try {
            return OrderStatus.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw new InvalidOrderStatusException(raw);
        }
    }
}
