package com.wms.notification.adapter.inbound.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wms.notification.domain.alert.AlertEnvelope;
import org.junit.jupiter.api.Test;

class AlertEnvelopeParserTest {

    private final AlertEnvelopeParser parser =
            new AlertEnvelopeParser(new ObjectMapper().registerModule(new JavaTimeModule()));

    @Test
    void parsesValidEnvelope() {
        String json = """
                {
                  "eventId": "11111111-1111-7111-8111-111111111111",
                  "eventType": "inventory.low-stock-detected",
                  "occurredAt": "2025-01-01T00:00:00Z",
                  "aggregateId": "warehouse-1",
                  "payload": {"sku": "ABC", "available": 0}
                }
                """;
        AlertEnvelope env = parser.parse(json, "wms.inventory.alert.v1");
        assertThat(env.eventType()).isEqualTo("inventory.low-stock-detected");
        assertThat(env.payload()).containsEntry("sku", "ABC");
    }

    @Test
    void missingEventIdThrowsIllegalArgument() {
        String json = """
                {"eventType":"x","occurredAt":"2025-01-01T00:00:00Z","payload":{}}
                """;
        assertThatThrownBy(() -> parser.parse(json, "wms.x.v1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void malformedJsonThrowsIllegalArgument() {
        assertThatThrownBy(() -> parser.parse("{not json", "wms.x.v1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void missingPayloadDefaultsToEmptyMap() {
        String json = """
                {
                  "eventId": "22222222-2222-7222-8222-222222222222",
                  "eventType": "inventory.adjusted",
                  "occurredAt": "2025-01-01T00:00:00Z"
                }
                """;
        AlertEnvelope env = parser.parse(json, "wms.inventory.adjusted.v1");
        assertThat(env.payload()).isEmpty();
    }
}
