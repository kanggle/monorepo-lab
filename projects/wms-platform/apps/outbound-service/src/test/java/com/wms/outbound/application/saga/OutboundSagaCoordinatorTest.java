package com.wms.outbound.application.saga;

import static org.assertj.core.api.Assertions.assertThat;

import com.wms.outbound.application.service.fakes.FakeOrderPersistencePort;
import com.wms.outbound.application.service.fakes.FakeSagaPersistencePort;
import com.wms.outbound.domain.model.Order;
import com.wms.outbound.domain.model.OrderLine;
import com.wms.outbound.domain.model.OrderSource;
import com.wms.outbound.domain.model.OrderStatus;
import com.wms.outbound.domain.model.OutboundSaga;
import com.wms.outbound.domain.model.SagaStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OutboundSagaCoordinatorTest {

    private static final Instant T0 = Instant.parse("2026-04-29T10:00:00Z");
    private final Clock clock = Clock.fixed(T0, ZoneOffset.UTC);

    private FakeSagaPersistencePort sagaPersistence;
    private FakeOrderPersistencePort orderPersistence;
    private OutboundSagaCoordinator coordinator;

    @BeforeEach
    void setUp() {
        sagaPersistence = new FakeSagaPersistencePort();
        orderPersistence = new FakeOrderPersistencePort();
        coordinator = new OutboundSagaCoordinator(sagaPersistence, orderPersistence, clock);
    }

    @Test
    void onInventoryReservedAdvancesRequestedToReserved() {
        UUID orderId = UUID.randomUUID();
        OutboundSaga saga = OutboundSaga.newRequested(UUID.randomUUID(), orderId, T0);
        sagaPersistence.save(saga);

        coordinator.onInventoryReserved(saga.sagaId());

        OutboundSaga loaded = sagaPersistence.findById(saga.sagaId()).orElseThrow();
        assertThat(loaded.status()).isEqualTo(SagaStatus.RESERVED);
    }

    @Test
    void onInventoryReleasedSettlesCancelledAndCancelsOrder() {
        Order order = newOrder(OrderStatus.PICKING);
        orderPersistence.save(order);
        UUID sagaId = UUID.randomUUID();
        OutboundSaga saga = new OutboundSaga(sagaId, order.getId(),
                SagaStatus.CANCELLATION_REQUESTED, sagaId, null, T0, T0, 0L);
        sagaPersistence.save(saga);

        coordinator.onInventoryReleased(sagaId);

        assertThat(sagaPersistence.findById(sagaId).orElseThrow().status())
                .isEqualTo(SagaStatus.CANCELLED);
        assertThat(orderPersistence.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void onInventoryConfirmedAdvancesShippedToCompleted() {
        UUID sagaId = UUID.randomUUID();
        OutboundSaga saga = new OutboundSaga(sagaId, UUID.randomUUID(),
                SagaStatus.SHIPPED, sagaId, null, T0, T0, 0L);
        sagaPersistence.save(saga);

        coordinator.onInventoryConfirmed(sagaId);

        assertThat(sagaPersistence.findById(sagaId).orElseThrow().status())
                .isEqualTo(SagaStatus.COMPLETED);
    }

    @Test
    void onReserveFailedSetsReserveFailedAndBackordersOrder() {
        Order order = newOrder(OrderStatus.PICKING);
        orderPersistence.save(order);
        UUID sagaId = UUID.randomUUID();
        OutboundSaga saga = OutboundSaga.newRequested(sagaId, order.getId(), T0);
        sagaPersistence.save(saga);

        coordinator.onReserveFailed(sagaId, "INSUFFICIENT_STOCK");

        assertThat(sagaPersistence.findById(sagaId).orElseThrow().status())
                .isEqualTo(SagaStatus.RESERVE_FAILED);
        assertThat(orderPersistence.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.BACKORDERED);
    }

    @Test
    void unknownSagaIdIsTolerated() {
        // Should be a graceful no-op, not throw.
        coordinator.onInventoryReserved(UUID.randomUUID());
        coordinator.onInventoryReleased(UUID.randomUUID());
        coordinator.onInventoryConfirmed(UUID.randomUUID());
        coordinator.onReserveFailed(UUID.randomUUID(), "x");
    }

    private static Order newOrder(OrderStatus status) {
        UUID orderId = UUID.randomUUID();
        return new Order(orderId, "ORD-" + orderId, OrderSource.MANUAL,
                UUID.randomUUID(), UUID.randomUUID(), null, null,
                status, 0L, T0, "u", T0, "u",
                List.of(new OrderLine(UUID.randomUUID(), orderId, 1,
                        UUID.randomUUID(), null, 1)));
    }
}
