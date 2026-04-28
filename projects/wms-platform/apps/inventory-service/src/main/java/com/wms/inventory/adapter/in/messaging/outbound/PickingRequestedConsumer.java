package com.wms.inventory.adapter.in.messaging.outbound;

import com.fasterxml.jackson.databind.JsonNode;
import com.wms.inventory.application.command.ReserveStockCommand;
import com.wms.inventory.application.port.in.ReserveStockUseCase;
import com.wms.inventory.application.port.out.EventDedupePort;
import java.util.ArrayList;
import java.util.List;
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
 * Consumes {@code wms.outbound.picking.requested.v1} and creates a
 * {@code Reservation} via {@link ReserveStockUseCase}.
 *
 * <p><strong>Layered idempotency.</strong> Two independent guards run in
 * series, both required by the architecture spec:
 * <ol>
 *   <li><strong>Outer (eventId dedupe, trait T8).</strong>
 *       {@link EventDedupePort#process(UUID, String, Runnable)} inserts a row
 *       into {@code inventory_event_dedupe} keyed by the envelope's
 *       {@code eventId}. A duplicate {@code eventId} (typical Kafka
 *       at-least-once redelivery) is short-circuited at the table's PK
 *       constraint and the use-case body is not re-executed.</li>
 *   <li><strong>Inner (pickingRequestId / aggregate-state guard).</strong>
 *       {@link com.wms.inventory.application.service.ReserveStockService}
 *       looks up an existing {@code Reservation} by
 *       {@code pickingRequestId} and short-circuits on cross-consumer races
 *       (e.g., a manual REST {@code POST /reservations} that arrived first
 *       with a different {@code eventId} but the same picking request).</li>
 * </ol>
 *
 * <p>Both layers are required: the eventId dedupe table is the
 * spec-mandated observable surface for "did this Kafka message already get
 * processed", while the pickingRequestId guard remains the source of truth
 * for true business-level idempotency across the REST and Kafka paths.
 *
 * <p>The consumer is {@code @Transactional} so the dedupe row, the
 * reservation insert, the inventory updates, and the outbox row commit (or
 * roll back) atomically. {@code EventDedupePersistenceAdapter} declares
 * {@code Propagation.MANDATORY}, ensuring the dedupe write joins this TX
 * rather than creating its own.
 *
 * <p>Authoritative consumed shape: {@code inventory-events.md} §C2.
 */
@Component
@Profile("!standalone")
public class PickingRequestedConsumer {

    private static final Logger log = LoggerFactory.getLogger(PickingRequestedConsumer.class);
    private static final String SYSTEM_ACTOR = "system:picking-requested-consumer";

    private final OutboundEventParser parser;
    private final EventDedupePort eventDedupePort;
    private final ReserveStockUseCase reserveStock;

    public PickingRequestedConsumer(OutboundEventParser parser,
                                    EventDedupePort eventDedupePort,
                                    ReserveStockUseCase reserveStock) {
        this.parser = parser;
        this.eventDedupePort = eventDedupePort;
        this.reserveStock = reserveStock;
    }

    @KafkaListener(
            topics = "${inventory.kafka.topics.outbound-picking-requested:wms.outbound.picking.requested.v1}",
            groupId = "${spring.kafka.consumer.group-id:inventory-service}"
    )
    @Transactional
    public void handle(@Payload String rawJson,
                       @Header(name = "kafka_receivedMessageKey", required = false) String key) {
        OutboundEventParser.Parsed envelope = parser.parse(rawJson);
        MDC.put("eventId", envelope.eventId().toString());
        MDC.put("consumer", "picking-requested");
        try {
            ReserveStockCommand command = buildCommand(envelope);
            log.debug("Processing outbound.picking.requested eventId={} pickingRequestId={}",
                    envelope.eventId(), command.pickingRequestId());
            EventDedupePort.Outcome outcome = eventDedupePort.process(
                    envelope.eventId(), envelope.eventType(),
                    () -> reserveStock.reserve(command));
            if (outcome == EventDedupePort.Outcome.IGNORED_DUPLICATE) {
                log.debug("outbound.picking.requested eventId={} already applied; skipping",
                        envelope.eventId());
            }
        } finally {
            MDC.remove("eventId");
            MDC.remove("consumer");
        }
    }

    private static ReserveStockCommand buildCommand(OutboundEventParser.Parsed envelope) {
        JsonNode payload = envelope.payload();
        UUID pickingRequestId = UUID.fromString(payload.get("pickingRequestId").asText());
        UUID warehouseId = UUID.fromString(payload.get("warehouseId").asText());
        int ttlSeconds = payload.has("ttlSeconds") && !payload.get("ttlSeconds").isNull()
                ? payload.get("ttlSeconds").asInt() : 86_400;
        JsonNode lines = payload.get("lines");
        if (lines == null || !lines.isArray() || lines.isEmpty()) {
            throw new IllegalArgumentException("outbound.picking.requested has no lines");
        }
        List<ReserveStockCommand.Line> commandLines = new ArrayList<>(lines.size());
        for (JsonNode line : lines) {
            UUID inventoryId = UUID.fromString(line.get("inventoryId").asText());
            int quantity = line.get("quantity").asInt();
            commandLines.add(new ReserveStockCommand.Line(inventoryId, quantity));
        }
        return new ReserveStockCommand(
                pickingRequestId, warehouseId, commandLines, ttlSeconds,
                envelope.eventId(), SYSTEM_ACTOR, null);
    }
}
