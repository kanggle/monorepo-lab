package com.example.order.application.service;

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
    public Order changeStatus(String orderId, OrderStatus targetStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        OrderStatus previousStatus = order.getStatus();

        switch (targetStatus) {
            case CONFIRMED -> order.confirm(clock);
            case SHIPPED -> order.ship(clock);
            case DELIVERED -> order.deliver(clock);
            case CANCELLED -> order.cancel(clock);
            default -> throw new IllegalArgumentException("Unsupported target status: " + targetStatus);
        }

        orderRepository.save(order);
        log.info("Admin changed order status: orderId={}, {} -> {}", orderId, previousStatus, targetStatus);
        return order;
    }
}
