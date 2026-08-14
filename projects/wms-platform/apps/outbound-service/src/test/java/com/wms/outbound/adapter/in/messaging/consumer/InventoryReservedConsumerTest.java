package com.wms.outbound.adapter.in.messaging.consumer;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.messaging.dedupe.EventDedupePort;
import com.wms.outbound.application.saga.OutboundSagaCoordinator;
import com.wms.outbound.application.saga.SagaIdResolver;
import com.wms.outbound.application.service.RecordReservedPickingService;
import com.wms.outbound.application.service.fakes.FakeOrderPersistencePort;
import com.wms.outbound.application.service.fakes.FakeOutboxWriterPort;
import com.wms.outbound.application.service.fakes.FakePickingPersistencePort;
import com.wms.outbound.application.service.fakes.FakeSagaPersistencePort;
import com.wms.outbound.domain.model.Order;
import com.wms.outbound.domain.model.OrderLine;
import com.wms.outbound.domain.model.OrderSource;
import com.wms.outbound.domain.model.OrderStatus;
import com.wms.outbound.domain.model.OutboundSaga;
import com.wms.outbound.domain.model.PickingRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InventoryReservedConsumerTest {

    private static final Instant T0 = Instant.parse("2026-04-29T10:00:00Z");
    private final Clock clock = Clock.fixed(T0, ZoneOffset.UTC);

    private EventEnvelopeParser parser;
    private FakeEventDedupePort dedupePort;
    private FakeSagaPersistencePort sagaPersistence;
    private FakeOrderPersistencePort orderPersistence;
    private FakePickingPersistencePort pickingPersistence;
    private OutboundSagaCoordinator coordinator;
    private InventoryReservedConsumer consumer;

    @BeforeEach
    void setUp() {
        parser = new EventEnvelopeParser(new ObjectMapper());
        dedupePort = new FakeEventDedupePort();
        sagaPersistence = new FakeSagaPersistencePort();
        orderPersistence = new FakeOrderPersistencePort();
        coordinator = new OutboundSagaCoordinator(sagaPersistence, orderPersistence,
                new FakeOutboxWriterPort(), clock);
        pickingPersistence = new FakePickingPersistencePort();
        InventoryConsumerSupport support = new InventoryConsumerSupport(
                parser, dedupePort, new SagaIdResolver(sagaPersistence));
        consumer = new InventoryReservedConsumer(support, coordinator,
                new RecordReservedPickingService(pickingPersistence, orderPersistence,
                        sagaPersistence, clock));
    }

    /**
     * TASK-BE-586 — the payload's {@code lines[]} must reach the picking request.
     *
     * <p>The two cells above deliberately send a payload with no {@code lines}
     * and no order behind the saga; they pin the saga transition only. This one
     * pins the half that did not exist at all: before ADR-MONO-066 nothing in
     * production ever created a {@code PickingRequest}, so the order stopped at
     * {@code PICKING} forever and {@code admin_shipment_summary} stayed empty.
     */
    @Test
    void freshEventAlsoRecordsThePickingRequest() {
        UUID sagaId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID skuId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        sagaPersistence.save(OutboundSaga.newRequested(sagaId, orderId, T0));

        OrderLine line = new OrderLine(UUID.randomUUID(), orderId, 1, skuId, null, 4);
        orderPersistence.save(new Order(orderId, "SO-1", OrderSource.MANUAL,
                UUID.randomUUID(), warehouseId, null, null, OrderStatus.PICKING,
                0L, T0, "creator", T0, "creator", List.of(line)));

        String json = """
                {
                  "eventId": "%s",
                  "eventType": "inventory.reserved",
                  "occurredAt": "2026-04-29T10:00:00.000Z",
                  "aggregateId": "%s",
                  "aggregateType": "reservation",
                  "payload": {
                    "sagaId": "%s",
                    "reservationId": "%s",
                    "pickingRequestId": "%s",
                    "warehouseId": "%s",
                    "lines": [
                      { "reservationLineId": "%s", "inventoryId": "%s",
                        "locationId": "%s", "skuId": "%s", "lotId": null,
                        "quantity": 4, "availableQtyAfter": 6, "reservedQtyAfter": 4 }
                    ]
                  }
                }
                """.formatted(
                        UUID.randomUUID(), UUID.randomUUID(),
                        sagaId, sagaId, sagaId, warehouseId,
                        UUID.randomUUID(), UUID.randomUUID(), locationId, skuId);

        consumer.onMessage(json, null);

        assertThat(sagaPersistence.findById(sagaId).orElseThrow().status().name())
                .isEqualTo("RESERVED");
        PickingRequest saved = pickingPersistence.findByOrderId(orderId).orElseThrow(
                () -> new AssertionError("consumer advanced the saga but recorded no PickingRequest"));
        assertThat(saved.getLines()).singleElement()
                .satisfies(l -> {
                    assertThat(l.getOrderLineId()).isEqualTo(line.getId());
                    assertThat(l.getLocationId()).isEqualTo(locationId);
                    assertThat(l.getQtyToPick()).isEqualTo(4);
                });
    }

    @Test
    void freshEventAdvancesSaga() {
        UUID sagaId = UUID.randomUUID();
        OutboundSaga saga = OutboundSaga.newRequested(sagaId, UUID.randomUUID(), T0);
        sagaPersistence.save(saga);

        String json = """
                {
                  "eventId": "%s",
                  "eventType": "inventory.reserved",
                  "occurredAt": "2026-04-29T10:00:00.000Z",
                  "aggregateId": "%s",
                  "aggregateType": "reservation",
                  "payload": {
                    "sagaId": "%s",
                    "reservationId": "%s",
                    "pickingRequestId": "%s",
                    "warehouseId": "%s"
                  }
                }
                """.formatted(
                        UUID.randomUUID(), UUID.randomUUID(),
                        sagaId, sagaId, sagaId, UUID.randomUUID());

        consumer.onMessage(json, null);

        assertThat(sagaPersistence.findById(sagaId).orElseThrow().status().name())
                .isEqualTo("RESERVED");
    }

    @Test
    void duplicateEventIsSkipped() {
        UUID sagaId = UUID.randomUUID();
        OutboundSaga saga = OutboundSaga.newRequested(sagaId, UUID.randomUUID(), T0);
        sagaPersistence.save(saga);

        UUID eventId = UUID.randomUUID();
        dedupePort.markAlreadySeen(eventId);

        String json = """
                {
                  "eventId": "%s",
                  "eventType": "inventory.reserved",
                  "occurredAt": "2026-04-29T10:00:00.000Z",
                  "aggregateId": "%s",
                  "aggregateType": "reservation",
                  "payload": {
                    "sagaId": "%s",
                    "reservationId": "%s",
                    "pickingRequestId": "%s",
                    "warehouseId": "%s"
                  }
                }
                """.formatted(
                        eventId, UUID.randomUUID(),
                        sagaId, sagaId, sagaId, UUID.randomUUID());

        consumer.onMessage(json, null);

        // Saga is still REQUESTED — coordinator never ran.
        assertThat(sagaPersistence.findById(sagaId).orElseThrow().status().name())
                .isEqualTo("REQUESTED");
    }

    /** Inner-class fake of EventDedupePort. Tracks seen ids. */
    private static class FakeEventDedupePort implements EventDedupePort {
        private final Set<UUID> seen = new HashSet<>();

        void markAlreadySeen(UUID eventId) {
            seen.add(eventId);
        }

        @Override
        public Outcome process(UUID eventId, String eventType, Runnable work) {
            if (seen.contains(eventId)) {
                return Outcome.IGNORED_DUPLICATE;
            }
            seen.add(eventId);
            work.run();
            return Outcome.APPLIED;
        }
    }
}
