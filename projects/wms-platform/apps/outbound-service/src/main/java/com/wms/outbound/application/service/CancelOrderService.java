package com.wms.outbound.application.service;

import com.wms.outbound.application.command.CancelOrderCommand;
import com.wms.outbound.application.port.in.CancelOrderUseCase;
import com.wms.outbound.application.port.out.OrderPersistencePort;
import com.wms.outbound.application.port.out.OutboxWriterPort;
import com.wms.outbound.application.port.out.SagaPersistencePort;
import com.wms.outbound.application.result.OrderResult;
import com.wms.outbound.domain.event.OrderCancelledEvent;
import com.wms.outbound.domain.event.PickingCancelledEvent;
import com.wms.outbound.domain.exception.OrderNotFoundException;
import com.wms.outbound.domain.model.Order;
import com.wms.outbound.domain.model.OrderStatus;
import com.wms.outbound.domain.model.OutboundSaga;
import com.wms.outbound.domain.model.SagaStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AC-09 implementation: cancels an order; emits {@code outbound.order.cancelled}
 * always; emits {@code outbound.picking.cancelled} when the saga still holds
 * a reservation that needs to be released by inventory-service.
 *
 * <p>Authorization is enforced in this layer (not the controller):
 * <ul>
 *   <li>{@code RECEIVED} / {@code PICKING} order: requires
 *       {@code ROLE_OUTBOUND_WRITE} (or admin).</li>
 *   <li>{@code PICKED} / {@code PACKING} / {@code PACKED} order
 *       (post-pick): requires {@code ROLE_OUTBOUND_ADMIN}.</li>
 *   <li>{@code SHIPPED}: forbidden — {@code OrderAlreadyShippedException}
 *       (raised by {@link Order#cancel}).</li>
 * </ul>
 */
@Service
public class CancelOrderService implements CancelOrderUseCase {

    private static final Logger log = LoggerFactory.getLogger(CancelOrderService.class);

    private static final String ROLE_OUTBOUND_WRITE = "ROLE_OUTBOUND_WRITE";
    private static final String ROLE_OUTBOUND_ADMIN = "ROLE_OUTBOUND_ADMIN";

    private static final Set<OrderStatus> POST_PICK_STATES = EnumSet.of(
            OrderStatus.PICKED, OrderStatus.PACKING, OrderStatus.PACKED);

    /**
     * Saga states whose cancellation requires inventory to release a
     * reservation (compensation event must be fired).
     */
    private static final Set<SagaStatus> RESERVATION_HELD_STATES = EnumSet.of(
            SagaStatus.RESERVED, SagaStatus.PICKING_CONFIRMED, SagaStatus.PACKING_CONFIRMED);

    private final OrderPersistencePort orderPersistence;
    private final SagaPersistencePort sagaPersistence;
    private final OutboxWriterPort outboxWriter;
    private final Clock clock;

    public CancelOrderService(OrderPersistencePort orderPersistence,
                              SagaPersistencePort sagaPersistence,
                              OutboxWriterPort outboxWriter,
                              Clock clock) {
        this.orderPersistence = orderPersistence;
        this.sagaPersistence = sagaPersistence;
        this.outboxWriter = outboxWriter;
        this.clock = clock;
    }

    @Override
    @Transactional
    public OrderResult cancel(CancelOrderCommand command) {
        Order order = orderPersistence.findById(command.orderId())
                .orElseThrow(() -> new OrderNotFoundException(command.orderId()));

        OrderStatus previousStatus = order.getStatus();

        // Authorization: post-pick cancel requires ADMIN.
        if (POST_PICK_STATES.contains(previousStatus)) {
            requireRole(command.callerRoles(), ROLE_OUTBOUND_ADMIN);
        } else {
            requireAnyRole(command.callerRoles(), ROLE_OUTBOUND_WRITE, ROLE_OUTBOUND_ADMIN);
        }

        // Optimistic lock check (T5 + outbound-service-api.md §1.4):
        // surface 409 CONFLICT before mutating state and writing the outbox,
        // not at JPA's deferred flush. expectedVersion < 0 means the caller
        // opted out of the early check (kept for legacy callers/tests).
        if (command.expectedVersion() >= 0 && order.getVersion() != command.expectedVersion()) {
            throw new ObjectOptimisticLockingFailureException(Order.class, command.orderId());
        }

        Instant now = clock.instant();
        // Domain.cancel enforces the SHIPPED guard (→ OrderAlreadyShippedException).
        order.cancel(command.reason(), now, command.actorId());
        Order saved = orderPersistence.save(order);

        // Saga side: request cancellation. Coordinator (or inventory.released
        // consumer) will settle to CANCELLED once inventory acknowledges.
        OutboundSaga saga = sagaPersistence.findByOrderId(order.getId()).orElse(null);
        boolean reservationHeld = false;
        if (saga != null) {
            reservationHeld = RESERVATION_HELD_STATES.contains(saga.status());
            saga.requestCancellation(now);
            if (saga.status() == SagaStatus.REQUESTED || saga.status() == SagaStatus.CANCELLATION_REQUESTED) {
                if (!reservationHeld) {
                    // No reservation yet → settle the saga immediately.
                    saga.cancelImmediately(now);
                }
            }
            saga = sagaPersistence.save(saga);
        }

        // Always emit order.cancelled.
        outboxWriter.publish(new OrderCancelledEvent(
                saved.getId(),
                saved.getOrderNo(),
                previousStatus.name(),
                command.reason(),
                now,
                now,
                command.actorId()));

        // Emit picking.cancelled compensation if the saga held a reservation.
        if (reservationHeld && saga != null) {
            outboxWriter.publish(new PickingCancelledEvent(
                    saga.sagaId(),
                    saga.pickingRequestId(),
                    saved.getId(),
                    command.reason(),
                    now,
                    command.actorId()));
        }

        log.info("order_cancelled orderId={} previousStatus={} reservationHeld={}",
                saved.getId(), previousStatus, reservationHeld);
        return OrderResultMapper.toResult(
                saved, saga,
                previousStatus.name(),
                command.reason(),
                now,
                command.actorId());
    }

    private static void requireRole(Set<String> roles, String required) {
        if (roles == null || !roles.contains(required)) {
            throw new AccessDeniedException("Role required: " + required);
        }
    }

    private static void requireAnyRole(Set<String> roles, String... required) {
        if (roles == null) {
            throw new AccessDeniedException("Role required: any of " + java.util.Arrays.toString(required));
        }
        for (String r : required) {
            if (roles.contains(r)) {
                return;
            }
        }
        throw new AccessDeniedException("Role required: any of " + java.util.Arrays.toString(required));
    }
}
