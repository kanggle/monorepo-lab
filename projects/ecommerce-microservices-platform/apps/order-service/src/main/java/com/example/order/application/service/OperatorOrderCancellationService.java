package com.example.order.application.service;

import com.example.order.application.dto.CancelOrderResult;
import com.example.order.application.event.OrderCancelledEvent;
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

/**
 * Operator-initiated manual order cancellation (TASK-BE-428): the console operator
 * cancels an order — most often a {@code BACKORDERED} one that will never be replenished
 * — through the internal endpoint. There is no requesting user, so ownership is NOT
 * checked (cf. {@link OrderCancellationService#cancelOrder}, the customer REST path).
 *
 * <p>Reuses the <b>existing</b> {@code order.cancelled} fan-out: publishing the event
 * triggers payment-service refund + promotion-service coupon restore. No new refund
 * machinery is built here. {@code BACKORDERED} is now {@link
 * com.example.order.domain.model.OrderStatus#isCancellable() cancellable}, so a held
 * order cancels like {@code PENDING}/{@code CONFIRMED}; a non-cancellable order
 * (SHIPPED/DELIVERED/terminal) surfaces {@code OrderCannotBeCancelledException} from the
 * domain (mapped to {@code 422} by the {@code GlobalExceptionHandler}).
 *
 * <p>Tenant-agnostic system path: the order is addressed by its globally-unique id
 * ({@link OrderRepository#findByIdAcrossTenants}); the order's immutable tenant is
 * preserved on save and stamped onto the emitted {@code order.cancelled} envelope.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperatorOrderCancellationService {

    private final OrderRepository orderRepository;
    private final OrderEventPublisher orderEventPublisher;
    private final OrderMetricsPort orderMetrics;
    private final Clock clock;

    @Transactional
    public CancelOrderResult cancel(String orderId, String reason) {
        Order order = orderRepository.findByIdAcrossTenants(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        String previousStatus = order.getStatus().name();
        order.cancel(CancelReason.OPERATOR, clock);
        orderRepository.save(order);
        orderMetrics.recordOrderCancelled("operator");
        orderMetrics.recordStatusTransition(previousStatus, order.getStatus().name());

        log.info("Order cancelled by operator. orderId={}, previousStatus={}, reason={}",
                orderId, previousStatus, reason);

        // Reuse the existing cancel fan-out: payment-service refunds + promotion-service restores.
        orderEventPublisher.publishOrderCancelled(
                OrderCancelledEvent.of(order.getOrderId(), order.getUserId(),
                        order.getUpdatedAt(), CancelReason.OPERATOR, clock));

        return new CancelOrderResult(order.getOrderId(), order.getStatus().name());
    }
}
