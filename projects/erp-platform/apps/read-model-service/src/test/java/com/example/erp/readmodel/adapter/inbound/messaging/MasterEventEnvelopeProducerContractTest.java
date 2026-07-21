package com.example.erp.readmodel.adapter.inbound.messaging;

import com.example.erp.readmodel.application.command.MasterChangeCommand;
import com.example.erp.readmodel.domain.common.ChangeKind;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cross-service contract guard (TASK-ERP-BE-032). The other envelope tests
 * hand-build a spec-shaped envelope and the producer unit test asserts the
 * producer shape, but NO test bridged the two — so the producer emitting a wire
 * the consumer rejects (top-level {@code aggregateId} absent) shipped silently and
 * routed every real event to the DLT.
 *
 * <p>This test pins BOTH directions of that seam by reconstructing the envelope
 * exactly as {@code masterdata-service}'s
 * {@code OutboxMasterdataEventPublisher.writeEvent} serialises it (the two services
 * are separate Gradle modules, so the wire is mirrored here rather than imported):
 * <ul>
 *   <li><b>current wire</b> — top-level {@code aggregateId} present → the consumer
 *       accepts it and maps a valid command;</li>
 *   <li><b>legacy 7-field wire</b> — {@code partitionKey} only, no top-level
 *       {@code aggregateId} → the consumer rejects it as invalid (the exact
 *       pre-fix defect). If the producer ever regresses to that shape, this case
 *       documents why the read-model would go dark.</li>
 * </ul>
 */
class MasterEventEnvelopeProducerContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final EnvelopeToCommandMapper mapper = new EnvelopeToCommandMapper(objectMapper);

    private static final String TOPIC = "erp.masterdata.department.changed.v1";

    /** The producer payload shape (OutboxMasterdataEventPublisher.payload — verbatim keys). */
    private Map<String, Object> producerPayload(String aggregateId) {
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("code", "SALES");
        after.put("name", "영업본부");
        after.put("status", "ACTIVE");
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("aggregateId", aggregateId);
        p.put("changeKind", "CREATED");
        p.put("tenantId", "erp");
        p.put("occurredAt", "2026-01-02T00:00:00Z");
        p.put("actor", "operator:op-1");
        p.put("before", null);
        p.put("after", after);
        p.put("reason", null);
        return p;
    }

    @Test
    void currentProducerWireIsAcceptedByConsumer() throws Exception {
        // Mirror of OutboxMasterdataEventPublisher.writeEvent (post TASK-ERP-BE-032).
        Map<String, Object> env = new LinkedHashMap<>();
        env.put("eventId", "0190aa00-0000-7000-8000-000000000001");
        env.put("eventType", "erp.masterdata.department.changed");
        env.put("source", "erp-platform-masterdata-service");
        env.put("occurredAt", "2026-01-01T00:00:00Z");
        env.put("schemaVersion", 1);
        env.put("tenantId", "erp");
        env.put("aggregateType", "department");
        env.put("aggregateId", "dept-1");
        env.put("partitionKey", "dept-1");
        env.put("payload", producerPayload("dept-1"));

        MasterChangeCommand cmd = mapper.map(objectMapper.writeValueAsString(env), TOPIC);

        assertThat(cmd.eventId()).isEqualTo("0190aa00-0000-7000-8000-000000000001");
        assertThat(cmd.aggregateId()).isEqualTo("dept-1");
        assertThat(cmd.changeKind()).isEqualTo(ChangeKind.CREATED);
        assertThat(cmd.afterString("code")).isEqualTo("SALES");
        // payload occurredAt is preferred over the envelope occurredAt.
        assertThat(cmd.occurredAt()).isEqualTo(java.time.Instant.parse("2026-01-02T00:00:00Z"));
    }

    @Test
    void legacySevenFieldWireWithoutTopLevelAggregateIdIsRejected() throws Exception {
        // The pre-fix producer wire: partitionKey carries the aggregate id, but there
        // is NO top-level aggregateId. MasterEventEnvelope.isValid() → false → DLT.
        Map<String, Object> env = new LinkedHashMap<>();
        env.put("eventId", "0190aa00-0000-7000-8000-000000000002");
        env.put("eventType", "erp.masterdata.department.changed");
        env.put("source", "erp-platform-masterdata-service");
        env.put("occurredAt", "2026-01-01T00:00:00Z");
        env.put("schemaVersion", 1);
        env.put("partitionKey", "dept-2"); // aggregate id lives ONLY here — the defect
        env.put("payload", producerPayload("dept-2"));

        String legacyJson = objectMapper.writeValueAsString(env);
        assertThatThrownBy(() -> mapper.map(legacyJson, TOPIC))
                .isInstanceOf(InvalidEnvelopeException.class)
                .hasMessageContaining("aggregateId");
    }
}
