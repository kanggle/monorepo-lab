package com.wms.master.adapter.out.messaging;

import com.example.common.id.UuidV7;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.master.application.result.LocationResult;
import com.wms.master.application.result.SkuResult;
import com.wms.master.application.result.WarehouseResult;
import com.wms.master.application.result.ZoneResult;
import com.wms.master.domain.event.DomainEvent;
import com.wms.master.domain.event.LocationCreatedEvent;
import com.wms.master.domain.event.LocationDeactivatedEvent;
import com.wms.master.domain.event.LocationReactivatedEvent;
import com.wms.master.domain.event.LocationUpdatedEvent;
import com.wms.master.domain.event.SkuCreatedEvent;
import com.wms.master.domain.event.SkuDeactivatedEvent;
import com.wms.master.domain.event.SkuReactivatedEvent;
import com.wms.master.domain.event.SkuUpdatedEvent;
import com.wms.master.domain.event.WarehouseCreatedEvent;
import com.wms.master.domain.event.WarehouseDeactivatedEvent;
import com.wms.master.domain.event.WarehouseReactivatedEvent;
import com.wms.master.domain.event.WarehouseUpdatedEvent;
import com.wms.master.domain.event.ZoneCreatedEvent;
import com.wms.master.domain.event.ZoneDeactivatedEvent;
import com.wms.master.domain.event.ZoneReactivatedEvent;
import com.wms.master.domain.event.ZoneUpdatedEvent;
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
            case ZoneCreatedEvent e -> Map.of("zone", ZoneResult.from(e.snapshot()));
            case ZoneUpdatedEvent e -> {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("zone", ZoneResult.from(e.snapshot()));
                payload.put("changedFields", e.changedFields());
                yield payload;
            }
            case ZoneDeactivatedEvent e -> {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("zone", ZoneResult.from(e.snapshot()));
                payload.put("reason", e.reason());
                yield payload;
            }
            case ZoneReactivatedEvent e -> Map.of("zone", ZoneResult.from(e.snapshot()));
            case LocationCreatedEvent e -> Map.of("location", LocationResult.from(e.snapshot()));
            case LocationUpdatedEvent e -> {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("location", LocationResult.from(e.snapshot()));
                payload.put("changedFields", e.changedFields());
                yield payload;
            }
            case LocationDeactivatedEvent e -> {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("location", LocationResult.from(e.snapshot()));
                payload.put("reason", e.reason());
                yield payload;
            }
            case LocationReactivatedEvent e -> Map.of("location", LocationResult.from(e.snapshot()));
            case SkuCreatedEvent e -> Map.of("sku", SkuResult.from(e.snapshot()));
            case SkuUpdatedEvent e -> {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("sku", SkuResult.from(e.snapshot()));
                payload.put("changedFields", e.changedFields());
                yield payload;
            }
            case SkuDeactivatedEvent e -> {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("sku", SkuResult.from(e.snapshot()));
                payload.put("reason", e.reason());
                yield payload;
            }
            case SkuReactivatedEvent e -> Map.of("sku", SkuResult.from(e.snapshot()));
        };
    }
}
