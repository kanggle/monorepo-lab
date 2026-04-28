package com.wms.inventory.adapter.in.messaging.inbound;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.inventory.application.command.ReceiveStockCommand;
import com.wms.inventory.application.command.ReceiveStockLineCommand;
import com.wms.inventory.application.port.in.ReceiveStockUseCase;
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
 * Consumes {@code wms.inbound.putaway.completed.v1}.
 *
 * <p>Behaviour: dedupe on {@code eventId} (T8), parse the envelope, build a
 * {@link ReceiveStockCommand}, and call {@link ReceiveStockUseCase}. The
 * use-case writes the inventory + movement + outbox rows in a single
 * transaction. On failure the entire transaction (including the dedupe row)
 * rolls back and the broker re-delivers; the error handler routes to DLT
 * after retries are exhausted.
 *
 * <p>Authoritative consumed-event shape: {@code inventory-events.md} §C1.
 */
@Component
@Profile("!standalone")
public class PutawayCompletedConsumer {

    private static final Logger log = LoggerFactory.getLogger(PutawayCompletedConsumer.class);
    private static final String SYSTEM_ACTOR = "system:putaway-consumer";

    private final ObjectMapper objectMapper;
    private final EventDedupePort dedupe;
    private final ReceiveStockUseCase receiveStock;

    public PutawayCompletedConsumer(ObjectMapper objectMapper,
                                    EventDedupePort dedupe,
                                    ReceiveStockUseCase receiveStock) {
        this.objectMapper = objectMapper;
        this.dedupe = dedupe;
        this.receiveStock = receiveStock;
    }

    @KafkaListener(
            topics = "${inventory.kafka.topics.inbound-putaway-completed:wms.inbound.putaway.completed.v1}",
            groupId = "${spring.kafka.consumer.group-id:inventory-service}"
    )
    @Transactional
    public void handle(@Payload String rawJson,
                       @Header(name = "kafka_receivedMessageKey", required = false) String key) {
        ParsedEvent event;
        try {
            event = parse(rawJson);
        } catch (JsonProcessingException e) {
            // Non-retryable — error handler routes to DLT.
            throw new IllegalArgumentException("Malformed inbound.putaway.completed JSON", e);
        }

        MDC.put("eventId", event.eventId.toString());
        MDC.put("consumer", "putaway-completed");
        try {
            EventDedupePort.Outcome outcome = dedupe.process(
                    event.eventId, event.eventType,
                    () -> receiveStock.receive(event.toCommand()));
            if (outcome == EventDedupePort.Outcome.IGNORED_DUPLICATE) {
                log.debug("inbound.putaway.completed eventId={} already applied; skipping",
                        event.eventId);
            }
        } finally {
            MDC.remove("eventId");
            MDC.remove("consumer");
        }
    }

    private ParsedEvent parse(String json) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(json);
        UUID eventId = UUID.fromString(requireText(root, "eventId"));
        String eventType = requireText(root, "eventType");
        JsonNode payload = root.get("payload");
        if (payload == null || payload.isNull()) {
            throw new IllegalArgumentException("inbound.putaway.completed missing payload");
        }
        UUID asnId = uuidOrNull(payload.get("asnId"));
        UUID warehouseId = uuidOrNull(payload.get("warehouseId"));
        if (warehouseId == null) {
            throw new IllegalArgumentException("inbound.putaway.completed missing warehouseId");
        }
        JsonNode lines = payload.get("lines");
        if (lines == null || !lines.isArray() || lines.isEmpty()) {
            throw new IllegalArgumentException("inbound.putaway.completed has no lines");
        }
        List<ReceiveStockLineCommand> lineCommands = new ArrayList<>(lines.size());
        for (JsonNode line : lines) {
            UUID locationId = UUID.fromString(requireText(line, "locationId"));
            UUID skuId = UUID.fromString(requireText(line, "skuId"));
            UUID lotId = uuidOrNull(line.get("lotId"));
            int qty = line.get("qtyReceived").asInt();
            if (qty <= 0) {
                throw new IllegalArgumentException(
                        "inbound.putaway.completed qtyReceived must be > 0, got: " + qty);
            }
            lineCommands.add(new ReceiveStockLineCommand(locationId, skuId, lotId, qty));
        }
        return new ParsedEvent(eventId, eventType, asnId, warehouseId, lineCommands);
    }

    private static String requireText(JsonNode parent, String field) {
        JsonNode node = parent.get(field);
        if (node == null || node.isNull() || !node.isTextual()) {
            throw new IllegalArgumentException("Missing or non-text field: " + field);
        }
        return node.asText();
    }

    private static UUID uuidOrNull(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return UUID.fromString(node.asText());
    }

    private record ParsedEvent(UUID eventId, String eventType, UUID asnId, UUID warehouseId,
                               List<ReceiveStockLineCommand> lines) {
        ReceiveStockCommand toCommand() {
            return new ReceiveStockCommand(eventId, warehouseId, asnId, lines, SYSTEM_ACTOR);
        }
    }
}
