package com.wms.outbound.application.saga;

import com.wms.outbound.application.port.out.OrderPersistencePort;
import com.wms.outbound.application.port.out.SagaPersistencePort;
import com.wms.outbound.domain.model.Order;
import com.wms.outbound.domain.model.OutboundSaga;
import com.wms.outbound.domain.model.SagaStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application-layer orchestrator for the outbound saga. Reacts to events
 * from {@code inventory-service} and drives the {@link OutboundSaga} state
 * machine, optionally advancing the {@link Order}'s lifecycle when needed.
 *
 * <p>Authoritative reference:
 * {@code specs/services/outbound-service/sagas/outbound-saga.md} and
 * {@code specs/services/outbound-service/state-machines/saga-status.md}.
 *
 * <p>All public methods declare {@code @Transactional(propagation = MANDATORY)}
 * — the saga consumers open the outer TX so the dedupe row, the saga
 * mutation, the order mutation (if any), and any compensation outbox row all
 * commit (or rollback) atomically.
 */
@Component
public class OutboundSagaCoordinator {

    private static final Logger log = LoggerFactory.getLogger(OutboundSagaCoordinator.class);

    private final SagaPersistencePort sagaPersistence;
    private final OrderPersistencePort orderPersistence;
    private final Clock clock;

    public OutboundSagaCoordinator(SagaPersistencePort sagaPersistence,
                                   OrderPersistencePort orderPersistence,
                                   Clock clock) {
        this.sagaPersistence = sagaPersistence;
        this.orderPersistence = orderPersistence;
        this.clock = clock;
    }

    /**
     * Saga step 1 success: advance {@code REQUESTED → RESERVED}.
     *
     * @param sagaId saga id correlated from the inventory reply
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void onInventoryReserved(UUID sagaId) {
        OutboundSaga saga = sagaPersistence.findById(sagaId).orElse(null);
        if (saga == null) {
            log.warn("inventory.reserved for unknown sagaId={}; skipping", sagaId);
            return;
        }
        Instant now = clock.instant();
        saga.onInventoryReserved(now);
        sagaPersistence.save(saga);
        log.info("saga_advanced sagaId={} to={}", sagaId, saga.status());
    }

    /**
     * Compensation: advance {@code CANCELLATION_REQUESTED → CANCELLED} and
     * cancel the order if it has not already moved to {@code CANCELLED}
     * (the user-initiated cancel typically handles that already; this is a
     * defensive sweep for sweeper-driven re-emission paths).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void onInventoryReleased(UUID sagaId) {
        OutboundSaga saga = sagaPersistence.findById(sagaId).orElse(null);
        if (saga == null) {
            log.warn("inventory.released for unknown sagaId={}; skipping", sagaId);
            return;
        }
        if (saga.status() == SagaStatus.CANCELLED) {
            // Already settled — nothing to do.
            return;
        }
        Instant now = clock.instant();
        saga.onInventoryReleased(now);
        sagaPersistence.save(saga);

        // Defensive: ensure the order ends up CANCELLED if it somehow lagged.
        Order order = orderPersistence.findById(saga.orderId()).orElse(null);
        if (order != null && order.getStatus() != com.wms.outbound.domain.model.OrderStatus.CANCELLED
                && order.getStatus() != com.wms.outbound.domain.model.OrderStatus.SHIPPED) {
            order.cancel("inventory released after compensation", now, "system:saga-coordinator");
            orderPersistence.save(order);
        }
        log.info("saga_cancelled sagaId={}", sagaId);
    }

    /**
     * Final saga step: {@code SHIPPED → COMPLETED}.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void onInventoryConfirmed(UUID sagaId) {
        OutboundSaga saga = sagaPersistence.findById(sagaId).orElse(null);
        if (saga == null) {
            log.warn("inventory.confirmed for unknown sagaId={}; skipping", sagaId);
            return;
        }
        Instant now = clock.instant();
        saga.onInventoryConfirmed(now);
        sagaPersistence.save(saga);
        log.info("saga_completed sagaId={}", sagaId);
    }

    /**
     * Operator confirmed picking (REST): {@code RESERVED → PICKING_CONFIRMED}.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void onPickingConfirmed(UUID sagaId) {
        OutboundSaga saga = sagaPersistence.findById(sagaId).orElse(null);
        if (saga == null) {
            log.warn("onPickingConfirmed for unknown sagaId={}; skipping", sagaId);
            return;
        }
        Instant now = clock.instant();
        saga.onPickingConfirmed(now);
        sagaPersistence.save(saga);
        log.info("saga_picking_confirmed sagaId={}", sagaId);
    }

    /**
     * All packing units sealed and confirmed (REST):
     * {@code PICKING_CONFIRMED → PACKING_CONFIRMED}.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void onPackingConfirmed(UUID sagaId) {
        OutboundSaga saga = sagaPersistence.findById(sagaId).orElse(null);
        if (saga == null) {
            log.warn("onPackingConfirmed for unknown sagaId={}; skipping", sagaId);
            return;
        }
        Instant now = clock.instant();
        saga.onPackingConfirmed(now);
        sagaPersistence.save(saga);
        log.info("saga_packing_confirmed sagaId={}", sagaId);
    }

    /**
     * Confirm-shipping use-case (REST): {@code PACKING_CONFIRMED → SHIPPED}.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void onShippingConfirmed(UUID sagaId) {
        OutboundSaga saga = sagaPersistence.findById(sagaId).orElse(null);
        if (saga == null) {
            log.warn("onShippingConfirmed for unknown sagaId={}; skipping", sagaId);
            return;
        }
        Instant now = clock.instant();
        saga.onShippingConfirmed(now);
        sagaPersistence.save(saga);
        log.info("saga_shipping_confirmed sagaId={}", sagaId);
    }

    /**
     * TMS push exhausted (after-commit): {@code SHIPPED → SHIPPED_NOT_NOTIFIED}.
     * Called from a separate TX (post-commit listener); the alert metric fires
     * downstream from this transition.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void onTmsNotifyFailed(UUID sagaId, String reason) {
        OutboundSaga saga = sagaPersistence.findById(sagaId).orElse(null);
        if (saga == null) {
            log.warn("onTmsNotifyFailed for unknown sagaId={}; skipping", sagaId);
            return;
        }
        Instant now = clock.instant();
        saga.onTmsNotifyFailed(reason, now);
        sagaPersistence.save(saga);
        log.warn("saga_shipped_not_notified sagaId={} reason={}", sagaId, reason);
    }

    /**
     * Reserve-failed compensation: saga {@code REQUESTED → RESERVE_FAILED};
     * order to {@code BACKORDERED}.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void onReserveFailed(UUID sagaId, String reason) {
        OutboundSaga saga = sagaPersistence.findById(sagaId).orElse(null);
        if (saga == null) {
            log.warn("inventory.adjusted(INSUFFICIENT_STOCK) for unknown sagaId={}; skipping", sagaId);
            return;
        }
        Instant now = clock.instant();
        saga.onReserveFailed(reason, now);
        sagaPersistence.save(saga);

        Order order = orderPersistence.findById(saga.orderId()).orElse(null);
        if (order != null && order.getStatus() != com.wms.outbound.domain.model.OrderStatus.BACKORDERED
                && order.getStatus() != com.wms.outbound.domain.model.OrderStatus.CANCELLED
                && order.getStatus() != com.wms.outbound.domain.model.OrderStatus.SHIPPED) {
            order.backorder(reason, now, "system:saga-coordinator");
            orderPersistence.save(order);
        }
        log.info("saga_reserve_failed sagaId={} reason={}", sagaId, reason);
    }
}
