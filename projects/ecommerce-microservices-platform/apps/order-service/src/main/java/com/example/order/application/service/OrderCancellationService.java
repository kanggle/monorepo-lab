package com.example.order.application.service;

import com.example.order.application.dto.CancelOrderResult;
import com.example.order.application.event.OrderCancelledEvent;
import com.example.order.application.exception.UnauthorizedOrderAccessException;
import com.example.order.application.port.OrderEventPublisher;
import com.example.order.application.port.OrderMetricsPort;
import com.example.order.domain.exception.OrderNotFoundException;
import com.example.order.domain.model.CancelReason;
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
public class OrderCancellationService {

    private final OrderRepository orderRepository;
    private final OrderEventPublisher orderEventPublisher;
    private final OrderMetricsPort orderMetrics;
    private final Clock clock;

    @Transactional
    public CancelOrderResult cancelOrder(String orderId, String requestingUserId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (!order.getUserId().equals(requestingUserId)) {
            throw new UnauthorizedOrderAccessException();
        }

        String previousStatus = order.getStatus().name();
        order.cancel(CancelReason.OPERATOR, clock);
        orderRepository.save(order);
        orderMetrics.recordOrderCancelled("user");
        orderMetrics.recordStatusTransition(previousStatus, order.getStatus().name());

        log.info("Order cancelled: orderId={}, userId={}", orderId, requestingUserId);

        orderEventPublisher.publishOrderCancelled(
                OrderCancelledEvent.of(order.getOrderId(), order.getUserId(),
                        order.getUpdatedAt(), CancelReason.OPERATOR, clock));

        return new CancelOrderResult(order.getOrderId(), order.getStatus().name());
    }
}
