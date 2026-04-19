package com.wms.master.adapter.out.messaging;

import com.example.common.id.UuidV7;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.master.application.result.WarehouseResult;
import com.wms.master.domain.event.DomainEvent;
import com.wms.master.domain.event.WarehouseCreatedEvent;
import com.wms.master.domain.event.WarehouseDeactivatedEvent;
import com.wms.master.domain.event.WarehouseReactivatedEvent;
import com.wms.master.domain.event.WarehouseUpdatedEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.MDC;

/**
 * Builds the outer envelope JSON per
 * {@code specs/contracts/events/master-events.md} § Global Envelope.
 * <p>
 * One envelope per {@link DomainEvent}, serialized as a self-contained string
 * suitable for writing to the outbox.
 */
public class EventEnvelopeSerializer {

    private static final String PRODUCER = "master-service";
    private static final int EVENT_VERSION = 1;

    private final ObjectMapper objectMapper;

    public EventEnvelopeSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String serialize(DomainEvent event) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", UuidV7.randomUuid().toString());
        envelope.put("eventType", event.eventType());
        envelope.put("eventVersion", EVENT_VERSION);
        envelope.put("occurredAt", event.occurredAt().toString());
        envelope.put("producer", PRODUCER);
        envelope.put("aggregateType", event.aggregateType());
        envelope.put("aggregateId", event.aggregateId().toString());
        envelope.put("traceId", currentTraceId());
        envelope.put("actorId", event.actorId());
        envelope.put("payload", buildPayload(event));

        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize event envelope", e);
        }
    }

    private static String currentTraceId() {
        String traceId = MDC.get("traceId");
        return traceId == null || traceId.isBlank() ? null : traceId;
    }

    private static Object buildPayload(DomainEvent event) {
        return switch (event) {
            case WarehouseCreatedEvent e -> Map.of("warehouse", WarehouseResult.from(e.snapshot()));
            case WarehouseUpdatedEvent e -> {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("warehouse", WarehouseResult.from(e.snapshot()));
                payload.put("changedFields", e.changedFields());
                yield payload;
            }
            case WarehouseDeactivatedEvent e -> {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("warehouse", WarehouseResult.from(e.snapshot()));
                payload.put("reason", e.reason());
                yield payload;
            }
            case WarehouseReactivatedEvent e -> Map.of("warehouse", WarehouseResult.from(e.snapshot()));
        };
    }
}
