package com.wms.outbound.adapter.in.messaging.consumer;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.outbound.application.port.out.EventDedupePort;
import com.wms.outbound.application.port.out.SagaPersistencePort;
import com.wms.outbound.application.saga.OutboundSagaCoordinator;
import com.wms.outbound.application.service.fakes.FakeOrderPersistencePort;
import com.wms.outbound.application.service.fakes.FakeSagaPersistencePort;
import com.wms.outbound.domain.model.OutboundSaga;
import com.wms.outbound.domain.model.SagaStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link InventoryReleasedConsumer}. Mirrors
 * {@code InventoryReservedConsumerTest} — port fakes only, no Mockito or
 * Testcontainers.
 *
 * <p>Covers AC-01 of TASK-BE-039 (consumer fresh + duplicate event paths)
 * and the saga step {@code CANCELLATION_REQUESTED → CANCELLED}.
 */
class InventoryReleasedConsumerTest {

    private static final Instant T0 = Instant.parse("2026-04-29T10:00:00Z");
    private final Clock clock = Clock.fixed(T0, ZoneOffset.UTC);

    private InventoryEventParser parser;
    private FakeEventDedupePort dedupePort;
    private FakeSagaPersistencePort sagaPersistence;
    private FakeOrderPersistencePort orderPersistence;
    private OutboundSagaCoordinator coordinator;
    private InventoryReleasedConsumer consumer;

    @BeforeEach
    void setUp() {
        parser = new InventoryEventParser(new ObjectMapper());
        dedupePort = new FakeEventDedupePort();
        sagaPersistence = new FakeSagaPersistencePort();
        orderPersistence = new FakeOrderPersistencePort();
        coordinator = new OutboundSagaCoordinator(sagaPersistence, orderPersistence, clock);
        consumer = new InventoryReleasedConsumer(parser, dedupePort, coordinator,
                (SagaPersistencePort) sagaPersistence);
    }

    @Test
    void freshEvent_advancesSagaToCancelled() {
        UUID sagaId = UUID.randomUUID();
        // Saga must already be in CANCELLATION_REQUESTED before the release
        // event lands (the user's cancel call moves it there).
        OutboundSaga saga = new OutboundSaga(
                sagaId, UUID.randomUUID(), SagaStatus.CANCELLATION_REQUESTED,
                sagaId, null, T0, T0, 0L);
        sagaPersistence.save(saga);

        String json = """
                {
                  "eventId": "%s",
                  "eventType": "inventory.released",
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

        assertThat(sagaPersistence.findById(sagaId).orElseThrow().status())
                .isEqualTo(SagaStatus.CANCELLED);
    }

    @Test
    void duplicateEvent_skipsCoordinator() {
        UUID sagaId = UUID.randomUUID();
        OutboundSaga saga = new OutboundSaga(
                sagaId, UUID.randomUUID(), SagaStatus.CANCELLATION_REQUESTED,
                sagaId, null, T0, T0, 0L);
        sagaPersistence.save(saga);

        UUID eventId = UUID.randomUUID();
        dedupePort.markAlreadySeen(eventId);

        String json = """
                {
                  "eventId": "%s",
                  "eventType": "inventory.released",
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

        // Saga is still CANCELLATION_REQUESTED — coordinator never ran.
        assertThat(sagaPersistence.findById(sagaId).orElseThrow().status())
                .isEqualTo(SagaStatus.CANCELLATION_REQUESTED);
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
