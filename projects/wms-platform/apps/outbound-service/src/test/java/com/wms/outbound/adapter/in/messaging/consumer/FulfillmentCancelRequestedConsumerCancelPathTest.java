package com.wms.outbound.adapter.in.messaging.consumer;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.outbound.application.service.CancelOrderService;
import com.wms.outbound.application.service.fakes.FakeCallerScopeProvider;
import com.wms.outbound.application.service.fakes.FakeOrderPersistencePort;
import com.wms.outbound.application.service.fakes.FakeOutboxWriterPort;
import com.wms.outbound.application.service.fakes.FakeSagaPersistencePort;
import com.wms.outbound.domain.event.OrderCancelledEvent;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * {@link FulfillmentCancelRequestedConsumer} wired to the <b>real</b>
 * {@link CancelOrderService} over in-memory fakes (TASK-BE-650, ADR-MONO-022 D4
 * v2(d) X-A).
 *
 * <p>The Mockito unit test proves what the consumer <em>asks for</em>; this one
 * proves what the existing use-case <em>does</em> with it: the existing compensation
 * and the existing {@code outbound.order.cancelled} emission fire unchanged for a
 * pre-pick order, and a post-pick order is left exactly as it was — no state change,
 * no outbox row — so the system path cannot reach the ADMIN-only cancel.
 */
class FulfillmentCancelRequestedConsumerCancelPathTest {

    private static final Instant T0 = Instant.parse("2026-10-23T10:00:00Z");

    private FakeOrderPersistencePort orderPersistence;
    private FakeSagaPersistencePort sagaPersistence;
    private FakeOutboxWriterPort outboxWriter;
    private FulfillmentCancelRequestedConsumer consumer;

    @BeforeEach
    void setUp() {
        orderPersistence = new FakeOrderPersistencePort();
        sagaPersistence = new FakeSagaPersistencePort();
        outboxWriter = new FakeOutboxWriterPort();
        // No security context in a Kafka consumer ⇒ D9 CallerScope is unrestricted.
        FakeCallerScopeProvider callerScope = new FakeCallerScopeProvider();
        CancelOrderService cancelOrderService = new CancelOrderService(
                orderPersistence, sagaPersistence, outboxWriter, callerScope,
                Clock.fixed(T0, ZoneOffset.UTC));
        consumer = new FulfillmentCancelRequestedConsumer(
                new EventEnvelopeParser(new ObjectMapper()),
                new FulfillmentCancelRequestedConsumerTest.FakeEventDedupePort(),
                orderPersistence,
                cancelOrderService);
    }

    @Test
    void pickingOrderWithReservationIsCancelledWithExistingCompensation() {
        Order order = saveOrder("ECO-7001", OrderStatus.PICKING);
        UUID sagaId = UUID.randomUUID();
        sagaPersistence.save(new OutboundSaga(sagaId, order.getId(), SagaStatus.RESERVED,
                sagaId, null, T0, T0, 0L));

        consumer.onMessage(event("ECO-7001"), null);

        assertThat(orderPersistence.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CANCELLED);
        assertThat(outboxWriter.countByType("outbound.order.cancelled")).isEqualTo(1);
        // Reservation held ⇒ the existing picking.cancelled compensation fires.
        assertThat(outboxWriter.countByType("outbound.picking.cancelled")).isEqualTo(1);

        OrderCancelledEvent cancelled = outboxWriter.published.stream()
                .filter(OrderCancelledEvent.class::isInstance)
                .map(OrderCancelledEvent.class::cast)
                .findFirst().orElseThrow();
        assertThat(cancelled.orderNo()).isEqualTo("ECO-7001");
        assertThat(cancelled.previousStatus()).isEqualTo("PICKING");
        assertThat(cancelled.reason()).isEqualTo("ORDER_CANCELLED");
        assertThat(cancelled.actorId()).isEqualTo("system:fulfillment-cancel-request");
    }

    @Test
    void receivedOrderWithoutReservationIsCancelledWithoutCompensation() {
        Order order = saveOrder("ECO-7002", OrderStatus.RECEIVED);
        sagaPersistence.save(OutboundSaga.newRequested(UUID.randomUUID(), order.getId(), T0));

        consumer.onMessage(event("ECO-7002"), null);

        assertThat(orderPersistence.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CANCELLED);
        assertThat(outboxWriter.countByType("outbound.order.cancelled")).isEqualTo(1);
        assertThat(outboxWriter.countByType("outbound.picking.cancelled")).isEqualTo(0);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"PICKED", "PACKING", "PACKED"})
    void postPickOrderIsLeftUntouched(OrderStatus status) {
        Order order = saveOrder("ECO-7101", status);
        UUID sagaId = UUID.randomUUID();
        sagaPersistence.save(new OutboundSaga(sagaId, order.getId(),
                SagaStatus.PICKING_CONFIRMED, sagaId, null, T0, T0, 0L));

        consumer.onMessage(event("ECO-7101"), null);

        assertThat(orderPersistence.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(status);
        assertThat(outboxWriter.published).isEmpty();
    }

    @Test
    void secondRequestForAnAlreadyCancelledOrderEmitsNothingMore() {
        Order order = saveOrder("ECO-7201", OrderStatus.PICKING);
        sagaPersistence.save(OutboundSaga.newRequested(UUID.randomUUID(), order.getId(), T0));

        consumer.onMessage(event("ECO-7201"), null);
        // A distinct envelope (new eventId) for the same order: dedupe does not
        // catch it, so the order terminal-state guard must.
        consumer.onMessage(event("ECO-7201"), null);

        assertThat(outboxWriter.countByType("outbound.order.cancelled")).isEqualTo(1);
    }

    private Order saveOrder(String orderNo, OrderStatus status) {
        UUID orderId = UUID.randomUUID();
        OrderLine line = new OrderLine(UUID.randomUUID(), orderId, 1,
                UUID.randomUUID(), null, null, 5);
        Order order = new Order(orderId, orderNo, OrderSource.FULFILLMENT_ECOMMERCE,
                UUID.randomUUID(), UUID.randomUUID(), null, null, null, "store-acme",
                status, 0L, T0, "system", T0, "system", List.of(line));
        orderPersistence.save(order);
        return order;
    }

    private static String event(String orderNo) {
        return FulfillmentCancelRequestedConsumerTest.event(UUID.randomUUID(), orderNo, "ORDER_CANCELLED");
    }
}
