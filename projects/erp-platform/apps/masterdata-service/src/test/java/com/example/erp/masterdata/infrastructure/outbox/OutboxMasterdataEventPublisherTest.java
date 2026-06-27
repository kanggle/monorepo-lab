package com.example.erp.masterdata.infrastructure.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.example.erp.masterdata.application.event.MasterdataEventPublisher;
import com.example.erp.masterdata.application.event.MasterdataEventPublisher.ChangeKind;
import com.example.erp.masterdata.domain.department.Department;
import com.example.erp.masterdata.domain.effectivedate.EffectivePeriod;
import com.example.erp.masterdata.infrastructure.persistence.jpa.MasterdataOutboxJpaEntity;
import com.example.erp.masterdata.infrastructure.persistence.jpa.MasterdataOutboxJpaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit test for the {@link OutboxMasterdataEventPublisher} write path
 * (TASK-ERP-BE-026 — outbox v2).
 *
 * <p>Asserts a domain event persists a {@code masterdata_outbox} row whose
 * wire-relevant fields are preserved exactly vs the v1
 * {@code BaseEventPublisher.writeEvent}: the canonical 7-field envelope
 * ({@code eventId, eventType, source, occurredAt, schemaVersion=1, partitionKey,
 * payload}) in that field order, the row {@code id} reused as the envelope
 * {@code eventId}, {@code aggregate_type}/{@code aggregate_id}/{@code event_type}
 * matching the v1 call, {@code partition_key} = aggregateId (the v1 Kafka key),
 * and the v1 payload shape — including the {@code before}/{@code after}/
 * {@code reason} keys written UNCONDITIONALLY (a null serialises as JSON null).
 */
class OutboxMasterdataEventPublisherTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-27T10:15:30Z"), ZoneOffset.UTC);
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final String TENANT = "erp";

    private final MasterdataOutboxJpaRepository repository = mock(MasterdataOutboxJpaRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OutboxMasterdataEventPublisher publisher =
            new OutboxMasterdataEventPublisher(repository, objectMapper, CLOCK);

    private Department department() {
        return Department.create("d-1", TENANT, "DEPT-1", "Sales", null,
                EffectivePeriod.openEnded(LocalDate.of(2026, 1, 1)), NOW);
    }

    @Test
    @DisplayName("publishDepartmentChanged CREATED → v2 row + canonical envelope + key fields")
    void publishDepartmentChangedCreated() throws Exception {
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("code", "DEPT-1");
        after.put("name", "Sales");

        publisher.publishDepartmentChanged(department(), ChangeKind.CREATED, "operator:op-1",
                null, after, null);

        MasterdataOutboxJpaEntity row = capturedRow();
        assertThat(row.getEventType()).isEqualTo(MasterdataEventPublisher.EVENT_DEPARTMENT_CHANGED);
        assertThat(row.getAggregateType()).isEqualTo("department");
        assertThat(row.getAggregateId()).isEqualTo("d-1");
        assertThat(row.getPartitionKey()).isEqualTo("d-1");
        assertThat(row.getOccurredAt()).isEqualTo(CLOCK.instant());
        assertThat(row.getPublishedAt()).isNull();

        JsonNode envelope = objectMapper.readTree(row.getPayload());
        // envelope eventId == row PK (header/payload identity)
        assertThat(envelope.get("eventId").asText()).isEqualTo(row.getEventId().toString());
        assertThat(envelope.get("eventType").asText())
                .isEqualTo(MasterdataEventPublisher.EVENT_DEPARTMENT_CHANGED);
        assertThat(envelope.get("source").asText()).isEqualTo("erp-platform-masterdata-service");
        assertThat(envelope.get("occurredAt").asText()).isEqualTo(CLOCK.instant().toString());
        assertThat(envelope.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(envelope.get("partitionKey").asText()).isEqualTo("d-1");

        JsonNode p = envelope.get("payload");
        assertThat(p.get("aggregateId").asText()).isEqualTo("d-1");
        assertThat(p.get("changeKind").asText()).isEqualTo("CREATED");
        assertThat(p.get("tenantId").asText()).isEqualTo("erp");
        assertThat(p.get("actor").asText()).isEqualTo("operator:op-1");
        assertThat(p.hasNonNull("occurredAt")).isTrue();
        // before/reason are written UNCONDITIONALLY as JSON null (v1 shape).
        assertThat(p.has("before")).isTrue();
        assertThat(p.get("before").isNull()).isTrue();
        assertThat(p.has("reason")).isTrue();
        assertThat(p.get("reason").isNull()).isTrue();
        // after carries the supplied snapshot.
        assertThat(p.get("after").get("code").asText()).isEqualTo("DEPT-1");
        assertThat(p.get("after").get("name").asText()).isEqualTo("Sales");
    }

    @Test
    @DisplayName("publishDepartmentChanged RETIRED → reason present, before+after present")
    void publishDepartmentChangedRetired() throws Exception {
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("status", "ACTIVE");
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("status", "RETIRED");

        publisher.publishDepartmentChanged(department(), ChangeKind.RETIRED, "operator:op-1",
                before, after, "reorg");

        JsonNode p = objectMapper.readTree(capturedRow().getPayload()).get("payload");
        assertThat(p.get("changeKind").asText()).isEqualTo("RETIRED");
        assertThat(p.get("reason").asText()).isEqualTo("reorg");
        assertThat(p.get("before").get("status").asText()).isEqualTo("ACTIVE");
        assertThat(p.get("after").get("status").asText()).isEqualTo("RETIRED");
    }

    private MasterdataOutboxJpaEntity capturedRow() {
        ArgumentCaptor<MasterdataOutboxJpaEntity> captor =
                ArgumentCaptor.forClass(MasterdataOutboxJpaEntity.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }
}
