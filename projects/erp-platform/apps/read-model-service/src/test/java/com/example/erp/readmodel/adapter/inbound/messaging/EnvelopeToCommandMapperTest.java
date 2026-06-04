package com.example.erp.readmodel.adapter.inbound.messaging;

import com.example.erp.readmodel.application.command.MasterChangeCommand;
import com.example.erp.readmodel.domain.common.ChangeKind;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the consumer envelope→command mapper: valid mapping per
 * aggregate + invalid-envelope detection (null eventId/payload/changeKind →
 * {@link InvalidEnvelopeException} → DLT, no retry).
 */
class EnvelopeToCommandMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final EnvelopeToCommandMapper mapper = new EnvelopeToCommandMapper(objectMapper);

    private static final String TOPIC = "erp.masterdata.department.changed.v1";

    @Test
    void mapsValidDepartmentCreatedEnvelope() {
        String json = """
                { "eventId": "evt-1", "eventType": "erp.masterdata.department.changed",
                  "occurredAt": "2026-01-01T00:00:00Z", "tenantId": "erp",
                  "source": "erp-platform-masterdata-service",
                  "aggregateType": "department", "aggregateId": "dept-1",
                  "payload": { "changeKind": "CREATED", "occurredAt": "2026-01-02T00:00:00Z",
                               "after": { "code": "SALES", "name": "영업본부", "parentId": "hq",
                                          "status": "ACTIVE",
                                          "effectivePeriod": { "effectiveFrom": "2026-01-01" } } } }
                """;

        MasterChangeCommand cmd = mapper.map(json, TOPIC);

        assertThat(cmd.eventId()).isEqualTo("evt-1");
        assertThat(cmd.aggregateId()).isEqualTo("dept-1");
        assertThat(cmd.changeKind()).isEqualTo(ChangeKind.CREATED);
        assertThat(cmd.afterString("code")).isEqualTo("SALES");
        assertThat(cmd.afterString("parentId")).isEqualTo("hq");
        assertThat(cmd.effectiveDate("effectiveFrom")).isEqualTo(java.time.LocalDate.parse("2026-01-01"));
        // payload occurredAt preferred over envelope occurredAt
        assertThat(cmd.occurredAt()).isEqualTo(java.time.Instant.parse("2026-01-02T00:00:00Z"));
    }

    @Test
    void retiredEnvelopeHasNullAfter() {
        // RETIRED carries after=null (producer contract); effective-dating is
        // sourced from after, so a RETIRED command resolves null effective dates
        // (the projection keeps its prior effective_to unless a non-null is given).
        String json = """
                { "eventId": "evt-r", "eventType": "erp.masterdata.employee.changed",
                  "occurredAt": "2026-01-01T00:00:00Z", "tenantId": "erp",
                  "aggregateType": "employee", "aggregateId": "emp-1",
                  "payload": { "changeKind": "RETIRED", "after": null } }
                """;
        MasterChangeCommand cmd = mapper.map(json, "erp.masterdata.employee.changed.v1");
        assertThat(cmd.changeKind()).isEqualTo(ChangeKind.RETIRED);
        assertThat(cmd.after()).isNull();
        assertThat(cmd.effectiveDate("effectiveTo")).isNull();
    }

    @Test
    void nullEventIdIsInvalidEnvelope() {
        String json = """
                { "eventId": null, "aggregateId": "dept-1",
                  "payload": { "changeKind": "CREATED", "after": {} } }
                """;
        assertThatThrownBy(() -> mapper.map(json, TOPIC))
                .isInstanceOf(InvalidEnvelopeException.class);
    }

    @Test
    void nullPayloadIsInvalidEnvelope() {
        String json = """
                { "eventId": "evt-1", "aggregateId": "dept-1", "payload": null }
                """;
        assertThatThrownBy(() -> mapper.map(json, TOPIC))
                .isInstanceOf(InvalidEnvelopeException.class);
    }

    @Test
    void unknownChangeKindIsInvalidEnvelope() {
        String json = """
                { "eventId": "evt-1", "aggregateId": "dept-1",
                  "payload": { "changeKind": "WAT", "after": {} } }
                """;
        assertThatThrownBy(() -> mapper.map(json, TOPIC))
                .isInstanceOf(InvalidEnvelopeException.class);
    }

    @Test
    void malformedJsonIsInvalidEnvelope() {
        assertThatThrownBy(() -> mapper.map("{ not json", TOPIC))
                .isInstanceOf(InvalidEnvelopeException.class);
    }
}
