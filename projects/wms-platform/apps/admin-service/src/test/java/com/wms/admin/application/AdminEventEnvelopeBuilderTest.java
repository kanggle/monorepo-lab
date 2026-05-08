package com.wms.admin.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AdminEventEnvelopeBuilderTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final AdminEventEnvelopeBuilder builder = new AdminEventEnvelopeBuilder(objectMapper);

    @Test
    void envelope_carriesRequiredFields() throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", "abc");
        Instant occurredAt = Instant.parse("2026-05-09T10:00:00Z");

        String json = builder.build("admin.user.created", "user", "user-1",
                "actor-1", occurredAt, payload);

        JsonNode node = objectMapper.readTree(json);
        assertThat(node.get("eventId").asText()).isNotBlank();
        assertThat(node.get("eventType").asText()).isEqualTo("admin.user.created");
        assertThat(node.get("eventVersion").asInt()).isEqualTo(1);
        assertThat(node.get("occurredAt").asText()).isEqualTo("2026-05-09T10:00:00Z");
        assertThat(node.get("producer").asText()).isEqualTo("admin-service");
        assertThat(node.get("aggregateType").asText()).isEqualTo("user");
        assertThat(node.get("aggregateId").asText()).isEqualTo("user-1");
        assertThat(node.get("actorId").asText()).isEqualTo("actor-1");
        assertThat(node.get("payload").get("userId").asText()).isEqualTo("abc");
    }

    @Test
    void envelope_eventIdsAreUniquePerCall() throws Exception {
        String first = builder.build("admin.user.created", "user", "u1", "a", Instant.now(), Map.of());
        String second = builder.build("admin.user.created", "user", "u1", "a", Instant.now(), Map.of());
        JsonNode firstNode = objectMapper.readTree(first);
        JsonNode secondNode = objectMapper.readTree(second);
        assertThat(firstNode.get("eventId").asText())
                .isNotEqualTo(secondNode.get("eventId").asText());
    }

    @Test
    void envelope_nullActorIdSerialisedAsNull() throws Exception {
        String json = builder.build("admin.user.created", "user", "u1", null, Instant.now(), Map.of());
        JsonNode node = objectMapper.readTree(json);
        assertThat(node.has("actorId")).isTrue();
        assertThat(node.get("actorId").isNull()).isTrue();
    }

    @Test
    void envelope_payloadIsNestedObject() throws Exception {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("foo", "bar");
        nested.put("count", 42);
        String json = builder.build("admin.user.created", "user", "u1", "a", Instant.now(), nested);
        JsonNode node = objectMapper.readTree(json);
        assertThat(node.get("payload").get("foo").asText()).isEqualTo("bar");
        assertThat(node.get("payload").get("count").asInt()).isEqualTo(42);
    }
}
