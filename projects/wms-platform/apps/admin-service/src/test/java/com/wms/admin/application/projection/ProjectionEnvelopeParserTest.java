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
        // No tenantId on this envelope — most event types carry none.
        assertThat(env.tenantId()).isNull();
    }

    // ── Envelope-level tenantId (ADR-MONO-065 § D2) ────────────────────────────

    @Test
    void readsEnvelopeLevelTenantId() {
        String json = "{\"eventId\":\"0191d8f0-1f0e-7c40-9d13-4a2c9e3f1234\","
                + "\"eventType\":\"outbound.order.received\","
                + "\"occurredAt\":\"2026-05-09T10:00:00.123Z\","
                + "\"tenantId\":\"demo-corp\","
                + "\"aggregateId\":\"abc\",\"payload\":{\"orderId\":\"x\"}}";

        assertThat(parser.parse(json, "wms.outbound.order.received.v1").tenantId())
                .isEqualTo("demo-corp");
    }

    /**
     * It is envelope-level, not payload-level. A {@code tenantId} sitting inside the
     * payload is a different field and must not be picked up — reading it would make
     * the isolation axis settable by whoever composes the payload.
     */
    @Test
    void ignoresATenantIdNestedInThePayload() {
        String json = "{\"eventId\":\"0191d8f0-1f0e-7c40-9d13-4a2c9e3f1234\","
                + "\"eventType\":\"outbound.order.received\","
                + "\"occurredAt\":\"2026-05-09T10:00:00.123Z\","
                + "\"payload\":{\"tenantId\":\"acme-corp\"}}";

        assertThat(parser.parse(json, "wms.outbound.order.received.v1").tenantId()).isNull();
    }

    /**
     * JSON null, a blank string and a non-text value all collapse to {@code null}.
     * A blank tenant reaching the read model would be a tenant nobody can match, and
     * a malformed optional field must not DLT an otherwise-valid event.
     */
    @Test
    void nullBlankAndNonTextTenantIdAllBecomeNull() {
        String prefix = "{\"eventId\":\"0191d8f0-1f0e-7c40-9d13-4a2c9e3f1234\","
                + "\"eventType\":\"outbound.order.received\","
                + "\"occurredAt\":\"2026-05-09T10:00:00.123Z\",\"payload\":{},\"tenantId\":";

        assertThat(parser.parse(prefix + "null}", "t").tenantId()).isNull();
        assertThat(parser.parse(prefix + "\"   \"}", "t").tenantId()).isNull();
        assertThat(parser.parse(prefix + "42}", "t").tenantId()).isNull();
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
