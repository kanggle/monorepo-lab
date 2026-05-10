package com.example.messaging.envelope;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("EventEnvelopeParser")
class EventEnvelopeParserTest {

    private EventEnvelopeParser parser;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        parser = new EventEnvelopeParser(objectMapper);
    }

    @Nested
    @DisplayName("happy path")
    class HappyPath {

        @Test
        @DisplayName("parses a fully populated envelope")
        void parsesFullEnvelope() {
            UUID eventId = UUID.fromString("0190dffb-7c5e-7000-8000-000000000001");
            String json = "{"
                    + "\"eventId\":\"" + eventId + "\","
                    + "\"eventType\":\"inventory.received\","
                    + "\"eventVersion\":1,"
                    + "\"occurredAt\":\"2026-05-10T00:00:00Z\","
                    + "\"producer\":\"inventory-service\","
                    + "\"aggregateType\":\"Stock\","
                    + "\"aggregateId\":\"agg-1\","
                    + "\"traceId\":\"trace-abc\","
                    + "\"actorId\":\"actor-1\","
                    + "\"payload\":{\"qty\":5}"
                    + "}";

            EventEnvelope env = parser.parse(json);

            assertThat(env.eventId()).isEqualTo(eventId);
            assertThat(env.eventType()).isEqualTo("inventory.received");
            assertThat(env.eventVersion()).isEqualTo(1);
            assertThat(env.occurredAt()).isEqualTo(Instant.parse("2026-05-10T00:00:00Z"));
            assertThat(env.producer()).isEqualTo("inventory-service");
            assertThat(env.aggregateType()).isEqualTo("Stock");
            assertThat(env.aggregateId()).isEqualTo("agg-1");
            assertThat(env.traceId()).isEqualTo("trace-abc");
            assertThat(env.actorId()).isEqualTo("actor-1");
            assertThat(env.payload().get("qty").asInt()).isEqualTo(5);
        }

        @Test
        @DisplayName("defaults eventVersion to 1 when absent")
        void defaultsEventVersion() {
            UUID eventId = UUID.fromString("0190dffb-7c5e-7000-8000-000000000002");
            String json = "{"
                    + "\"eventId\":\"" + eventId + "\","
                    + "\"eventType\":\"x.y\","
                    + "\"occurredAt\":\"2026-05-10T00:00:00Z\","
                    + "\"aggregateType\":\"X\","
                    + "\"aggregateId\":\"id-1\","
                    + "\"payload\":{}"
                    + "}";

            EventEnvelope env = parser.parse(json);

            assertThat(env.eventVersion()).isEqualTo(1);
        }

        @Test
        @DisplayName("treats traceId / actorId / producer as optional")
        void optionalFieldsMayBeMissing() {
            UUID eventId = UUID.fromString("0190dffb-7c5e-7000-8000-000000000003");
            String json = "{"
                    + "\"eventId\":\"" + eventId + "\","
                    + "\"eventType\":\"x.y\","
                    + "\"occurredAt\":\"2026-05-10T00:00:00Z\","
                    + "\"aggregateType\":\"X\","
                    + "\"aggregateId\":\"id-1\","
                    + "\"payload\":{}"
                    + "}";

            EventEnvelope env = parser.parse(json);

            assertThat(env.traceId()).isNull();
            assertThat(env.actorId()).isNull();
            assertThat(env.producer()).isNull();
        }
    }

    @Nested
    @DisplayName("malformed input")
    class Malformed {

        @Test
        @DisplayName("throws IllegalArgumentException on invalid JSON")
        void invalidJson() {
            assertThatThrownBy(() -> parser.parse("not-json"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("malformed event envelope JSON");
        }

        @Test
        @DisplayName("throws when required field is missing")
        void requiredFieldMissing() {
            String json = "{\"eventType\":\"x.y\"}";

            assertThatThrownBy(() -> parser.parse(json))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("missing required field");
        }

        @Test
        @DisplayName("throws when payload is missing")
        void payloadMissing() {
            UUID eventId = UUID.fromString("0190dffb-7c5e-7000-8000-000000000004");
            String json = "{"
                    + "\"eventId\":\"" + eventId + "\","
                    + "\"eventType\":\"x.y\","
                    + "\"occurredAt\":\"2026-05-10T00:00:00Z\","
                    + "\"aggregateType\":\"X\","
                    + "\"aggregateId\":\"id-1\""
                    + "}";

            assertThatThrownBy(() -> parser.parse(json))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("missing payload");
        }

        @Test
        @DisplayName("throws when occurredAt is not a valid Instant")
        void invalidOccurredAt() {
            UUID eventId = UUID.fromString("0190dffb-7c5e-7000-8000-000000000005");
            String json = "{"
                    + "\"eventId\":\"" + eventId + "\","
                    + "\"eventType\":\"x.y\","
                    + "\"occurredAt\":\"not-a-date\","
                    + "\"aggregateType\":\"X\","
                    + "\"aggregateId\":\"id-1\","
                    + "\"payload\":{}"
                    + "}";

            assertThatThrownBy(() -> parser.parse(json))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("malformed envelope timestamp");
        }
    }
}
