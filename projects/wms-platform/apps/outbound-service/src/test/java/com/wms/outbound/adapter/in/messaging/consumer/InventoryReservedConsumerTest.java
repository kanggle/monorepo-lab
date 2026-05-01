package com.wms.outbound.adapter.in.messaging.consumer;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.outbound.application.port.out.EventDedupePort;
import com.wms.outbound.application.port.out.SagaPersistencePort;
import com.wms.outbound.application.saga.OutboundSagaCoordinator;
import com.wms.outbound.application.service.fakes.FakeOrderPersistencePort;
import com.wms.outbound.application.service.fakes.FakeSagaPersistencePort;
import com.wms.outbound.domain.model.OutboundSaga;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InventoryReservedConsumerTest {

    private static final Instant T0 = Instant.parse("2026-04-29T10:00:00Z");
    private final Clock clock = Clock.fixed(T0, ZoneOffset.UTC);

    private InventoryEventParser parser;
    private FakeEventDedupePort dedupePort;
    private FakeSagaPersistencePort sagaPersistence;
    private FakeOrderPersistencePort orderPersistence;
    private OutboundSagaCoordinator coordinator;
    private InventoryReservedConsumer consumer;

    @BeforeEach
    void setUp() {
        parser = new InventoryEventParser(new ObjectMapper());
        dedupePort = new FakeEventDedupePort();
        sagaPersistence = new FakeSagaPersistencePort();
        orderPersistence = new FakeOrderPersistencePort();
        coordinator = new OutboundSagaCoordinator(sagaPersistence, orderPersistence, clock);
        consumer = new InventoryReservedConsumer(parser, dedupePort, coordinator,
                (SagaPersistencePort) sagaPersistence);
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
