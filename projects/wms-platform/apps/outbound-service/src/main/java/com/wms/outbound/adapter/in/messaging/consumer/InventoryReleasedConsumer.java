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
 * Consumes {@code wms.inventory.released.v1} and finalises the saga's
 * cancellation path: {@code CANCELLATION_REQUESTED → CANCELLED} (or no-op
 * if already cancelled).
 *
 * <p>Layered idempotency identical to {@link InventoryReservedConsumer}.
 */
@Component
@Profile("!standalone")
public class InventoryReleasedConsumer {

    private static final Logger log = LoggerFactory.getLogger(InventoryReleasedConsumer.class);

    private final InventoryEventParser parser;
    private final EventDedupePort eventDedupePort;
    private final OutboundSagaCoordinator coordinator;
    private final SagaPersistencePort sagaPersistence;

    public InventoryReleasedConsumer(InventoryEventParser parser,
                                     EventDedupePort eventDedupePort,
                                     OutboundSagaCoordinator coordinator,
                                     SagaPersistencePort sagaPersistence) {
        this.parser = parser;
        this.eventDedupePort = eventDedupePort;
        this.coordinator = coordinator;
        this.sagaPersistence = sagaPersistence;
    }

    @KafkaListener(
            topics = "${outbound.kafka.topics.inventory-released:wms.inventory.released.v1}",
            groupId = "${spring.kafka.consumer.group-id:outbound-service}"
    )
    @Transactional
    public void onMessage(@Payload String rawJson,
                          @Header(name = "kafka_receivedMessageKey", required = false) String key) {
        InventoryEventEnvelope envelope = parser.parse(rawJson);
        MDC.put("eventId", envelope.eventId().toString());
        MDC.put("consumer", "inventory-released");
        try {
            EventDedupePort.Outcome outcome = eventDedupePort.process(
                    envelope.eventId(), envelope.eventType(),
                    () -> applyReleased(envelope));
            if (outcome == EventDedupePort.Outcome.IGNORED_DUPLICATE) {
                log.debug("inventory.released eventId={} already applied; skipping",
                        envelope.eventId());
            }
        } finally {
            MDC.remove("eventId");
            MDC.remove("consumer");
        }
    }

    private void applyReleased(InventoryEventEnvelope envelope) {
        UUID sagaId = resolveSagaId(envelope);
        if (sagaId == null) {
            log.warn("inventory.released without correlation keys; skipping payload={}",
                    envelope.payload());
            return;
        }
        coordinator.onInventoryReleased(sagaId);
    }

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
