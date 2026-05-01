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
 * Consumes {@code wms.inventory.confirmed.v1} and advances the saga
 * {@code SHIPPED → COMPLETED}.
 *
 * <p>The {@code SHIPPED} state itself is set by
 * {@code ConfirmShippingUseCase} (TASK-BE-038); this consumer only handles
 * the final saga-completion step.
 */
@Component
@Profile("!standalone")
public class InventoryConfirmedConsumer {

    private static final Logger log = LoggerFactory.getLogger(InventoryConfirmedConsumer.class);

    private final InventoryEventParser parser;
    private final EventDedupePort eventDedupePort;
    private final OutboundSagaCoordinator coordinator;
    private final SagaPersistencePort sagaPersistence;

    public InventoryConfirmedConsumer(InventoryEventParser parser,
                                      EventDedupePort eventDedupePort,
                                      OutboundSagaCoordinator coordinator,
                                      SagaPersistencePort sagaPersistence) {
        this.parser = parser;
        this.eventDedupePort = eventDedupePort;
        this.coordinator = coordinator;
        this.sagaPersistence = sagaPersistence;
    }

    @KafkaListener(
            topics = "${outbound.kafka.topics.inventory-confirmed:wms.inventory.confirmed.v1}",
            groupId = "${spring.kafka.consumer.group-id:outbound-service}"
    )
    @Transactional
    public void onMessage(@Payload String rawJson,
                          @Header(name = "kafka_receivedMessageKey", required = false) String key) {
        InventoryEventEnvelope envelope = parser.parse(rawJson);
        MDC.put("eventId", envelope.eventId().toString());
        MDC.put("consumer", "inventory-confirmed");
        try {
            EventDedupePort.Outcome outcome = eventDedupePort.process(
                    envelope.eventId(), envelope.eventType(),
                    () -> applyConfirmed(envelope));
            if (outcome == EventDedupePort.Outcome.IGNORED_DUPLICATE) {
                log.debug("inventory.confirmed eventId={} already applied; skipping",
                        envelope.eventId());
            }
        } finally {
            MDC.remove("eventId");
            MDC.remove("consumer");
        }
    }

    private void applyConfirmed(InventoryEventEnvelope envelope) {
        UUID sagaId = resolveSagaId(envelope);
        if (sagaId == null) {
            log.warn("inventory.confirmed without correlation keys; skipping payload={}",
                    envelope.payload());
            return;
        }
        coordinator.onInventoryConfirmed(sagaId);
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
