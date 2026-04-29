package com.wms.outbound.adapter.in.messaging.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.wms.outbound.application.port.out.EventDedupePort;
import com.wms.outbound.application.port.out.SagaPersistencePort;
import com.wms.outbound.application.saga.OutboundSagaCoordinator;
import com.wms.outbound.domain.model.OutboundSaga;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumes {@code wms.inventory.reserved.v1} and advances the matching saga
 * to {@code RESERVED} via {@link OutboundSagaCoordinator}.
 *
 * <p>Layered idempotency:
 * <ol>
 *   <li><b>Outer (eventId dedupe, T8).</b>
 *       {@link EventDedupePort#process} inserts a row into
 *       {@code outbound_event_dedupe} keyed by the envelope's
 *       {@code eventId}; a duplicate {@code eventId} short-circuits at the
 *       PK constraint.</li>
 *   <li><b>Inner (state-machine guard).</b> {@link OutboundSaga#onInventoryReserved}
 *       is a no-op if the saga is already {@code RESERVED}.</li>
 * </ol>
 *
 * <p>The consumer is {@code @Transactional} so the dedupe row, the saga
 * mutation, and any outbox writes commit atomically. The dedupe adapter
 * declares {@code Propagation.MANDATORY}; this method opens the outer TX.
 */
@Component
@Profile("!standalone")
public class InventoryReservedConsumer {

    private static final Logger log = LoggerFactory.getLogger(InventoryReservedConsumer.class);

    private final InventoryEventParser parser;
    private final EventDedupePort eventDedupePort;
    private final OutboundSagaCoordinator coordinator;
    private final SagaPersistencePort sagaPersistence;

    public InventoryReservedConsumer(InventoryEventParser parser,
                                     EventDedupePort eventDedupePort,
                                     OutboundSagaCoordinator coordinator,
                                     SagaPersistencePort sagaPersistence) {
        this.parser = parser;
        this.eventDedupePort = eventDedupePort;
        this.coordinator = coordinator;
        this.sagaPersistence = sagaPersistence;
    }

    @KafkaListener(
            topics = "${outbound.kafka.topics.inventory-reserved:wms.inventory.reserved.v1}",
            groupId = "${spring.kafka.consumer.group-id:outbound-service}"
    )
    @Transactional
    public void onMessage(@Payload String rawJson,
                          @Header(name = "kafka_receivedMessageKey", required = false) String key) {
        InventoryEventEnvelope envelope = parser.parse(rawJson);
        MDC.put("eventId", envelope.eventId().toString());
        MDC.put("consumer", "inventory-reserved");
        try {
            EventDedupePort.Outcome outcome = eventDedupePort.process(
                    envelope.eventId(), envelope.eventType(),
                    () -> applyReserved(envelope));
            if (outcome == EventDedupePort.Outcome.IGNORED_DUPLICATE) {
                log.debug("inventory.reserved eventId={} already applied; skipping",
                        envelope.eventId());
            }
        } finally {
            MDC.remove("eventId");
            MDC.remove("consumer");
        }
    }

    private void applyReserved(InventoryEventEnvelope envelope) {
        UUID sagaId = resolveSagaId(envelope);
        if (sagaId == null) {
            log.warn("inventory.reserved without correlation keys; skipping payload={}",
                    envelope.payload());
            return;
        }
        coordinator.onInventoryReserved(sagaId);
    }

    /**
     * Try to extract {@code sagaId} from the envelope per outbound-events.md
     * §C1; if not present, fall back to looking up the saga by
     * {@code pickingRequestId} / {@code reservationId}.
     */
    private UUID resolveSagaId(InventoryEventEnvelope envelope) {
        JsonNode payload = envelope.payload();
        JsonNode sagaIdNode = payload.get("sagaId");
        if (sagaIdNode != null && !sagaIdNode.isNull() && sagaIdNode.isTextual()) {
            return UUID.fromString(sagaIdNode.asText());
        }
        JsonNode pickingRequestIdNode = payload.get("pickingRequestId");
        if (pickingRequestIdNode == null || pickingRequestIdNode.isNull()) {
            pickingRequestIdNode = payload.get("reservationId");
        }
        if (pickingRequestIdNode != null && !pickingRequestIdNode.isNull()
                && pickingRequestIdNode.isTextual()) {
            UUID pickingRequestId = UUID.fromString(pickingRequestIdNode.asText());
            Optional<OutboundSaga> saga = sagaPersistence.findByPickingRequestId(pickingRequestId);
            return saga.map(OutboundSaga::sagaId).orElse(null);
        }
        return null;
    }
}
