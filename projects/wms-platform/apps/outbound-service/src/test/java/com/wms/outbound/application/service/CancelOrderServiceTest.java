package com.wms.outbound.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wms.outbound.application.command.CancelOrderCommand;
import com.wms.outbound.application.result.OrderResult;
import com.wms.outbound.application.service.fakes.FakeOrderPersistencePort;
import com.wms.outbound.application.service.fakes.FakeOutboxWriterPort;
import com.wms.outbound.application.service.fakes.FakeSagaPersistencePort;
import com.wms.outbound.domain.exception.OrderAlreadyShippedException;
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
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;

class CancelOrderServiceTest {

    private static final Instant T0 = Instant.parse("2026-04-29T10:00:00Z");
    private final Clock fixedClock = Clock.fixed(T0, ZoneOffset.UTC);

    private FakeOrderPersistencePort orderPersistence;
    private FakeSagaPersistencePort sagaPersistence;
    private FakeOutboxWriterPort outboxWriter;
    private CancelOrderService service;

    @BeforeEach
    void setUp() {
        orderPersistence = new FakeOrderPersistencePort();
        sagaPersistence = new FakeSagaPersistencePort();
        outboxWriter = new FakeOutboxWriterPort();
        service = new CancelOrderService(orderPersistence, sagaPersistence,
                outboxWriter, fixedClock);
    }

    @Test
    void cancelPickingOrderInRequestedSagaSettlesImmediately() {
        Order order = orderInState(OrderStatus.PICKING);
        orderPersistence.save(order);
        OutboundSaga saga = OutboundSaga.newRequested(UUID.randomUUID(), order.getId(), T0);
        sagaPersistence.save(saga);

        CancelOrderCommand cmd = new CancelOrderCommand(
                order.getId(), "customer changed mind", 0L, "user-1",
                Set.of("ROLE_OUTBOUND_WRITE"));

        OrderResult result = service.cancel(cmd);

        assertThat(result.status()).isEqualTo("CANCELLED");
        assertThat(outboxWriter.countByType("outbound.order.cancelled")).isEqualTo(1);
        // Pre-pick path — no compensation event needed.
        assertThat(outboxWriter.countByType("outbound.picking.cancelled")).isEqualTo(0);
    }

    @Test
    void cancelPickingOrderInReservedSagaEmitsPickingCancelledCompensation() {
        Order order = orderInState(OrderStatus.PICKING);
        orderPersistence.save(order);
        UUID sagaId = UUID.randomUUID();
        OutboundSaga saga = new OutboundSaga(
                sagaId, order.getId(), SagaStatus.RESERVED,
                sagaId, null, T0, T0, 0L);
        sagaPersistence.save(saga);

        CancelOrderCommand cmd = new CancelOrderCommand(
                order.getId(), "customer changed mind", 0L, "user-1",
                Set.of("ROLE_OUTBOUND_WRITE"));

        OrderResult result = service.cancel(cmd);

        assertThat(result.status()).isEqualTo("CANCELLED");
        assertThat(outboxWriter.countByType("outbound.order.cancelled")).isEqualTo(1);
        assertThat(outboxWriter.countByType("outbound.picking.cancelled")).isEqualTo(1);
    }

    @Test
    void cancelPickedOrderRequiresAdmin() {
        Order order = orderInState(OrderStatus.PICKED);
        orderPersistence.save(order);
        UUID sagaId = UUID.randomUUID();
        sagaPersistence.save(new OutboundSaga(sagaId, order.getId(),
                SagaStatus.PICKING_CONFIRMED, sagaId, null, T0, T0, 0L));

        CancelOrderCommand cmdNoAdmin = new CancelOrderCommand(
                order.getId(), "ops decision", 0L, "user-1",
                Set.of("ROLE_OUTBOUND_WRITE"));
        assertThatThrownBy(() -> service.cancel(cmdNoAdmin))
                .isInstanceOf(AccessDeniedException.class);

        CancelOrderCommand cmdWithAdmin = new CancelOrderCommand(
                order.getId(), "ops decision", 0L, "admin-1",
                Set.of("ROLE_OUTBOUND_ADMIN"));
        OrderResult result = service.cancel(cmdWithAdmin);
        assertThat(result.status()).isEqualTo("CANCELLED");
        assertThat(outboxWriter.countByType("outbound.picking.cancelled")).isEqualTo(1);
    }

    @Test
    void cancelResultPopulatesContractCancelFields() {
        // AC-02: response body must carry previousStatus, cancelledReason,
        // cancelledAt, cancelledBy per outbound-service-api.md §1.4.
        Order order = orderInState(OrderStatus.PICKING);
        orderPersistence.save(order);
        OutboundSaga saga = OutboundSaga.newRequested(UUID.randomUUID(), order.getId(), T0);
        sagaPersistence.save(saga);

        CancelOrderCommand cmd = new CancelOrderCommand(
                order.getId(), "customer requested", 0L, "user-42",
                Set.of("ROLE_OUTBOUND_WRITE"));

        OrderResult result = service.cancel(cmd);

        assertThat(result.previousStatus()).isEqualTo("PICKING");
        assertThat(result.cancelledReason()).isEqualTo("customer requested");
        assertThat(result.cancelledAt()).isEqualTo(T0);
        assertThat(result.cancelledBy()).isEqualTo("user-42");
        assertThat(result.status()).isEqualTo("CANCELLED");
        assertThat(result.sagaState()).isEqualTo("CANCELLED");
    }

    @Test
    void cancelWithStaleVersionRaisesOptimisticLockingFailure() {
        // AC-04: expectedVersion mismatch must surface 409 CONFLICT BEFORE
        // any state mutation or outbox write.
        Order order = orderInState(OrderStatus.PICKING); // version=0
        orderPersistence.save(order);
        sagaPersistence.save(OutboundSaga.newRequested(
                UUID.randomUUID(), order.getId(), T0));

        CancelOrderCommand staleCmd = new CancelOrderCommand(
                order.getId(), "stale", 99L /* != current 0 */, "user-1",
                Set.of("ROLE_OUTBOUND_WRITE"));

        assertThatThrownBy(() -> service.cancel(staleCmd))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        // No outbox row written, no domain mutation.
        assertThat(outboxWriter.published).isEmpty();
        assertThat(orderPersistence.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PICKING);
    }

    @Test
    void cancelShippedOrderThrowsOrderAlreadyShipped() {
        Order order = orderInState(OrderStatus.SHIPPED);
        orderPersistence.save(order);
        UUID sagaId = UUID.randomUUID();
        sagaPersistence.save(new OutboundSaga(sagaId, order.getId(),
                SagaStatus.SHIPPED, sagaId, null, T0, T0, 0L));

        CancelOrderCommand cmd = new CancelOrderCommand(
                order.getId(), "too late", 0L, "admin-1",
                Set.of("ROLE_OUTBOUND_ADMIN"));

        assertThatThrownBy(() -> service.cancel(cmd))
                .isInstanceOf(OrderAlreadyShippedException.class);
    }

    private static Order orderInState(OrderStatus status) {
        UUID orderId = UUID.randomUUID();
        OrderLine line = new OrderLine(UUID.randomUUID(), orderId, 1,
                UUID.randomUUID(), null, 5);
        return new Order(orderId, "ORD-" + orderId, OrderSource.MANUAL,
                UUID.randomUUID(), UUID.randomUUID(),
                null, null, status, 0L,
                T0, "creator", T0, "creator",
                List.of(line));
    }
}
