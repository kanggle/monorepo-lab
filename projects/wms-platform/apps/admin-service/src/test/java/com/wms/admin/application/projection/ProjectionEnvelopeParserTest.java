package com.wms.admin.application.projection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ProjectionEnvelopeParserTest {

    private final ProjectionEnvelopeParser parser = new ProjectionEnvelopeParser(new ObjectMapper());

    @Test
    void parsesValidEnvelope() {
        String json = "{\"eventId\":\"0191d8f0-1f0e-7c40-9d13-4a2c9e3f1234\","
                + "\"eventType\":\"master.warehouse.created\","
                + "\"occurredAt\":\"2026-05-09T10:00:00.123Z\","
                + "\"aggregateId\":\"abc\",\"payload\":{\"warehouse\":{\"id\":\"x\"}}}";

        ProjectionEnvelope env = parser.parse(json, "wms.master.warehouse.v1");

        assertThat(env.eventType()).isEqualTo("master.warehouse.created");
        assertThat(env.occurredAt()).isNotNull();
        assertThat(env.payload().path("warehouse").path("id").asText()).isEqualTo("x");
    }

    @Test
    void rejectsMissingEventId() {
        String json = "{\"eventType\":\"x.y.z\"}";
        assertThatThrownBy(() -> parser.parse(json, "topic"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventId");
    }

    @Test
    void rejectsMalformedJson() {
        String json = "{not-valid";
        assertThatThrownBy(() -> parser.parse(json, "topic"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Malformed");
    }
}
