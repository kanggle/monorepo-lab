package com.example.order.application.service;

import com.example.order.application.event.OrderConfirmedEvent;
import com.example.order.application.port.OrderEventPublisher;
import com.example.order.application.port.OrderMetricsPort;
import com.example.order.domain.exception.OrderNotFoundException;
import com.example.order.domain.model.Order;
import com.example.order.domain.model.OrderItem;
import com.example.order.domain.model.OrderStatus;
import com.example.order.domain.model.ShippingAddress;
import com.example.order.domain.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderConfirmationService {

    private final OrderRepository orderRepository;
    private final OrderEventPublisher orderEventPublisher;
    private final OrderMetricsPort orderMetrics;
    private final Clock clock;

    @Transactional
    public void confirmOrder(String orderId) {
        Order order = orderRepository.findByIdAcrossTenants(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        if (order.getStatus() == OrderStatus.CANCELLED) {
            // TASK-BE-646: a reservation that committed before product-service consumed this
            // order's OrderCancelled (e.g. a restock re-reserving a backordered order the operator
            // just cancelled) still emits ORDER_RESERVED. A CANCELLED order can never confirm, and
            // product-service restores that stock when it consumes the OrderCancelled (RESERVED →
            // restore). Throwing here would only retry this message into the DLT, so end it as a
            // no-op. Order.confirm itself still rejects CANCELLED — the admin endpoint relies on it.
            log.warn("ORDER_RESERVED for a CANCELLED order ignored; product-service releases the "
                    + "reservation on OrderCancelled. orderId={}", orderId);
            return;
        }
        String previousStatus = order.getStatus().name();
        boolean changed = order.confirm(clock);
        orderRepository.save(order);
        if (changed) {
            orderMetrics.recordOrderConfirmed();
            orderMetrics.recordStatusTransition(previousStatus, order.getStatus().name());
            publishOrderConfirmed(order);
            log.info("Order confirmed: orderId={}", orderId);
        }
    }

    private void publishOrderConfirmed(Order order) {
        List<OrderConfirmedEvent.Line> lines = order.getItems().stream()
                .map(this::toLine)
                .toList();
        orderEventPublisher.publishOrderConfirmed(OrderConfirmedEvent.of(
                order.getOrderId(),
                order.getUserId(),
                Instant.now(clock),
                lines,
                toShippingAddress(order.getShippingAddress()),
                clock));
    }

    private OrderConfirmedEvent.Line toLine(OrderItem item) {
        // ecommerce sellable-unit id: prefer the variant, fall back to the product.
        String sku = item.getVariantId() != null && !item.getVariantId().isBlank()
                ? item.getVariantId()
                : item.getProductId();
        return new OrderConfirmedEvent.Line(
                sku, item.getProductId(), item.getVariantId(), item.getQuantity());
    }

    private OrderConfirmedEvent.ShippingAddress toShippingAddress(ShippingAddress address) {
        if (address == null) {
            return null;
        }
        String full = address.getAddress2() == null || address.getAddress2().isBlank()
                ? address.getAddress1()
                : address.getAddress1() + " " + address.getAddress2();
        return new OrderConfirmedEvent.ShippingAddress(
                address.getRecipient(), full, address.getPhone());
    }
}
