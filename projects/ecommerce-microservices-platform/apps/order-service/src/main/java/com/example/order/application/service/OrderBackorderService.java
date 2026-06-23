package com.example.order.application.service;

import com.example.order.application.port.OrderMetricsPort;
import com.example.order.domain.model.Order;
import com.example.order.domain.model.OrderStatus;
import com.example.order.domain.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Holds a paid order for backorder when the product-service reservation saga could not
 * all-or-nothing reserve stock (TASK-BE-428). Driven by the
 * {@code OrderReservationFailedConsumer} ({@code product.product.reservation-failed});
 * no stock has been decremented — the order is parked in {@code BACKORDERED} until a
 * later replenishment re-reserves it and confirms it via the normal
 * {@code StockChanged(ORDER_RESERVED)} → {@link OrderConfirmationService} path.
 *
 * <p>Tenant-agnostic system path (like {@link OrderBackorderCancellationService}): the
 * order is addressed by its globally-unique id. Status-safe + idempotent — a no-op (no
 * save, no event) when the order is missing or has already advanced past {@code PENDING}
 * (the domain {@link Order#markBackordered(Clock)} returns {@code false}). No outbound
 * event is published for the {@code BACKORDERED} transition itself — it is observed via
 * the order read API.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderBackorderService {

    private final OrderRepository orderRepository;
    private final OrderMetricsPort orderMetrics;
    private final Clock clock;

    @Transactional
    public void markBackordered(String orderId) {
        Order order = orderRepository.findByIdAcrossTenants(orderId).orElse(null);
        if (order == null) {
            log.warn("Reservation-failed for unknown order, skipping. orderId={}", orderId);
            return;
        }

        OrderStatus previousStatus = order.getStatus();
        boolean changed = order.markBackordered(clock);
        if (!changed) {
            // Late reservation-failed for an already-advanced (or already-BACKORDERED)
            // order — no-op, no save, no event.
            log.info("Reservation-failed is a no-op — order not PENDING. orderId={}, status={}",
                    orderId, previousStatus);
            return;
        }

        orderRepository.save(order);
        orderMetrics.recordOrderBackordered();
        orderMetrics.recordStatusTransition(previousStatus.name(), order.getStatus().name());

        log.info("Order held for backorder (paid, stock short). orderId={}, previousStatus={}",
                orderId, previousStatus);
    }
}
