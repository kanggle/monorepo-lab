package com.wms.admin.application;

import com.example.common.id.UuidV7;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Builds the per-event JSON envelope per
 * {@code specs/contracts/events/admin-events.md § Global Envelope}.
 *
 * <p>One method per event type — keeps the envelope structure consistent
 * (eventId, eventType, eventVersion, occurredAt, producer, aggregateType,
 * aggregateId, traceId, actorId, payload) while letting the caller build the
 * shape-specific {@code payload} block.
 *
 * <p>{@code traceId} is read from MDC. {@code actorId} is supplied by the
 * application service (typically from {@code X-Actor-Id} header).
 */
@Component
public class AdminEventEnvelopeBuilder {

    private static final String PRODUCER = "admin-service";
    private static final int EVENT_VERSION = 1;

    private final ObjectMapper objectMapper;

    public AdminEventEnvelopeBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String build(String eventType, String aggregateType, String aggregateId,
                        String actorId, Instant occurredAt, Map<String, Object> payload) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", UuidV7.randomString());
        envelope.put("eventType", eventType);
        envelope.put("eventVersion", EVENT_VERSION);
        envelope.put("occurredAt", occurredAt.toString());
        envelope.put("producer", PRODUCER);
        envelope.put("aggregateType", aggregateType);
        envelope.put("aggregateId", aggregateId);
        envelope.put("traceId", currentTraceId());
        envelope.put("actorId", actorId);
        envelope.put("payload", payload);
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise admin event envelope", e);
        }
    }

    private static String currentTraceId() {
        String traceId = MDC.get("traceId");
        return traceId == null || traceId.isBlank() ? null : traceId;
    }
}
