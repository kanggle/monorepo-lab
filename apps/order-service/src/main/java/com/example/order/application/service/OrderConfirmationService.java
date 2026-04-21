package com.example.order.application.service;

import com.example.order.application.port.OrderMetricsPort;
import com.example.order.domain.exception.OrderNotFoundException;
import com.example.order.domain.model.Order;
import com.example.order.domain.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderConfirmationService {

    private final OrderRepository orderRepository;
    private final OrderMetricsPort orderMetrics;
    private final Clock clock;

    @Transactional
    public void confirmOrder(String orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        String previousStatus = order.getStatus().name();
        boolean changed = order.confirm(clock);
        orderRepository.save(order);
        if (changed) {
            orderMetrics.recordOrderConfirmed();
            orderMetrics.recordStatusTransition(previousStatus, order.getStatus().name());
            log.info("Order confirmed: orderId={}", orderId);
        }
    }
}
