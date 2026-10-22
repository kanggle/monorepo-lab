package com.example.order.application.service;

import com.example.order.application.dto.AdminOrderStatusChangeResult;
import com.example.order.application.dto.CancelOrderResult;
import com.example.order.application.exception.InvalidOrderStatusException;
import com.example.order.domain.exception.InvalidOrderException;
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
    private final OperatorOrderCancellationService operatorOrderCancellationService;

    @Transactional
    public AdminOrderStatusChangeResult changeStatus(String orderId, String targetStatusRaw) {
        OrderStatus targetStatus = parseStatus(targetStatusRaw);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        OrderStatus previousStatus = order.getStatus();

        switch (targetStatus) {
            case CONFIRMED -> {
                // TASK-BE-648 (BACKORDERED first closed by TASK-BE-645): an operator never
                // confirms, from ANY current status. Confirmation belongs to the payment /
                // stock-reservation saga (OrderConfirmationService — StockChanged(ORDER_RESERVED),
                // the restock re-reservation, the stale-paid forward-confirm), which reserves
                // stock and emits OrderConfirmed (order-events.md § OrderConfirmed). This
                // endpoint did neither: it saved CONFIRMED with no reservation and no event, and
                // a later payment then found Order.confirm == false and never emitted
                // OrderConfirmed — and an unpaid one fell out of the PENDING-only stuck sweep.
                // An already-CONFIRMED order is refused too (no idempotent 200): CONFIRMED is
                // simply not in this endpoint's vocabulary (order-api.md). Guarded here, not in
                // the domain, so Order.confirm stays open to the saga.
                throw new InvalidOrderException(
                        "An order cannot be confirmed by an operator (current status: " + previousStatus
                                + "); orders are confirmed only by the payment / stock-reservation saga. "
                                + "An operator may only cancel an order (from PENDING, CONFIRMED or BACKORDERED)");
            }
            case CANCELLED -> {
                // TASK-BE-646: this is the console's cancel. It must compensate exactly like the
                // internal operator cancel — refund / void the payment, release the stock
                // reservation (a BACKORDERED hold included, so a later restock cannot bind to a
                // cancelled order), restore the coupon — so it runs the SAME application path:
                // cancel + save + operator metric + order.cancelled(OPERATOR) in this transaction.
                // The order was loaded tenant-scoped above, so the delegate does not re-load it
                // across tenants. An order already CANCELLED is refused by the domain (422)
                // before anything is saved or published: a repeated click emits nothing twice.
                CancelOrderResult cancelled = operatorOrderCancellationService.cancel(
                        order, "admin status change");
                log.info("Admin changed order status: orderId={}, {} -> {}",
                        orderId, previousStatus, targetStatus);
                return new AdminOrderStatusChangeResult(cancelled.orderId(), cancelled.status());
            }
            // SHIPPED / DELIVERED are NOT operator-settable on this endpoint. The Order
            // reaches those states solely via the shipping-driven return-leg
            // (ShippingStatusChanged -> OrderShippingService.markShipped/markDelivered,
            // ADR-MONO-022 §D7). Submitting them here falls through to default and is
            // rejected (400 INVALID_ORDER_REQUEST) to prevent order/shipping divergence.
            default -> throw new IllegalArgumentException("Unsupported target status: " + targetStatus);
        }
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
