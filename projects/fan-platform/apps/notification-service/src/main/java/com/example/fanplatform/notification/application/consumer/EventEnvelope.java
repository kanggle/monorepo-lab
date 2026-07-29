package com.example.fanplatform.notification.application.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The canonical fan-platform Kafka envelope (architecture.md § Consume
 * Semantics): {@code eventId / eventType / schemaVersion / payload} (plus
 * {@code source / occurredAt / partitionKey}, not read here). Extracted by
 * TASK-FAN-BE-035 so envelope validation and DLQ classification
 * ({@link MalformedEventException} vs {@link UnsupportedSchemaVersionException})
 * are shared by {@link MembershipEventParser} and {@link CommunityEventParser}
 * instead of duplicated.
 *
 * <p>{@code payload} is exposed as the raw object {@link JsonNode}; each caller
 * reads its own event-specific fields off it via {@link JsonFields}.
 */
record EventEnvelope(String eventId, String eventType, JsonNode payload) {

    /**
     * Validates and extracts the envelope. Statement order is load-bearing
     * (architecture.md § Consume Semantics): JSON parse → root-is-object →
     * {@code eventId} → {@code eventType} → schemaVersion gate →
     * payload-is-object. Reordering (e.g. reading {@code payload} before the
     * schemaVersion gate) silently reclassifies a DLQ diagnostic from
     * {@link UnsupportedSchemaVersionException} to {@link MalformedEventException}
     * — keep this order unchanged.
     *
     * @param supportedSchemaVersion the caller's own {@code SUPPORTED_SCHEMA_VERSION}
     *     constant. Intentionally not hard-coded here so the two event families
     *     can diverge later.
     */
    static EventEnvelope parse(ObjectMapper objectMapper, String rawValue, int supportedSchemaVersion) {
        JsonNode root;
        try {
            root = objectMapper.readTree(rawValue);
        } catch (Exception e) {
            throw new MalformedEventException("Unparseable envelope JSON: " + e.getMessage());
        }
        if (root == null || root.isNull() || !root.isObject()) {
            throw new MalformedEventException("Envelope is not a JSON object");
        }

        String eventId = JsonFields.requireText(root, "eventId");
        String eventType = JsonFields.requireText(root, "eventType");

        int schemaVersion = root.path("schemaVersion").asInt(-1);
        if (schemaVersion != supportedSchemaVersion) {
            throw new UnsupportedSchemaVersionException(schemaVersion, eventType);
        }

        JsonNode payload = root.get("payload");
        if (payload == null || payload.isNull() || !payload.isObject()) {
            throw new MalformedEventException("Missing payload for event " + eventType);
        }

        return new EventEnvelope(eventId, eventType, payload);
    }
}
