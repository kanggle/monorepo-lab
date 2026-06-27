package com.example.erp.masterdata.infrastructure.outbox;

import com.example.common.id.UuidV7;
import com.example.erp.masterdata.application.event.MasterdataEventPublisher;
import com.example.erp.masterdata.domain.businesspartner.BusinessPartner;
import com.example.erp.masterdata.domain.costcenter.CostCenter;
import com.example.erp.masterdata.domain.department.Department;
import com.example.erp.masterdata.domain.employee.Employee;
import com.example.erp.masterdata.domain.jobgrade.JobGrade;
import com.example.erp.masterdata.infrastructure.persistence.jpa.MasterdataOutboxJpaEntity;
import com.example.erp.masterdata.infrastructure.persistence.jpa.MasterdataOutboxJpaRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * {@link MasterdataEventPublisher} implementation (TASK-ERP-BE-026 — outbox v1 → v2).
 *
 * <p>Builds the canonical event envelope and persists a {@code masterdata_outbox}
 * row in the caller's transaction (the {@code AbstractOutboxPublisher} /
 * {@code OutboxRow} path — ADR-MONO-004 § 5, mirroring finance account-service's
 * {@code OutboxAccountEventPublisher}). The {@code MasterdataOutboxPublisher}
 * relay forwards the row to Kafka asynchronously; downstream consumers dedupe on
 * the envelope {@code eventId} (at-least-once).
 *
 * <p><b>Wire-shape preserved.</b> The envelope is the EXACT 7-field shape the
 * previous {@code BaseEventPublisher} path emitted —
 * {@code {eventId, eventType, source, occurredAt, schemaVersion, partitionKey,
 * payload}}, {@code source = "erp-platform-masterdata-service"}, every payload
 * field/order unchanged. The {@code before}/{@code after}/{@code reason} payload
 * keys are written UNCONDITIONALLY (a {@code null} value serialises as JSON
 * {@code null}) — byte-identical to the v1
 * {@code MasterdataEventPublisher.payload}. The only change: the envelope
 * {@code eventId} now equals the {@code masterdata_outbox} PK (both UUIDv7) so the
 * Kafka {@code eventId} header matches the payload.
 */
@Component
public class OutboxMasterdataEventPublisher implements MasterdataEventPublisher {

    static final String SOURCE = "erp-platform-masterdata-service";
    private static final int SCHEMA_VERSION = 1;

    private final MasterdataOutboxJpaRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OutboxMasterdataEventPublisher(MasterdataOutboxJpaRepository outboxRepository,
                                          ObjectMapper objectMapper,
                                          Clock clock) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public void publishDepartmentChanged(Department d, ChangeKind kind, String actor,
                                         Map<String, Object> before,
                                         Map<String, Object> after, String reason) {
        writeEvent(AGG_DEPARTMENT, d.getId(), EVENT_DEPARTMENT_CHANGED,
                payload(d.getId(), d.getTenantId(), kind, actor, before, after, reason));
    }

    @Override
    public void publishEmployeeChanged(Employee e, ChangeKind kind, String actor,
                                       Map<String, Object> before,
                                       Map<String, Object> after, String reason) {
        writeEvent(AGG_EMPLOYEE, e.getId(), EVENT_EMPLOYEE_CHANGED,
                payload(e.getId(), e.getTenantId(), kind, actor, before, after, reason));
    }

    @Override
    public void publishJobGradeChanged(JobGrade g, ChangeKind kind, String actor,
                                       Map<String, Object> before,
                                       Map<String, Object> after, String reason) {
        writeEvent(AGG_JOBGRADE, g.getId(), EVENT_JOBGRADE_CHANGED,
                payload(g.getId(), g.getTenantId(), kind, actor, before, after, reason));
    }

    @Override
    public void publishCostCenterChanged(CostCenter c, ChangeKind kind, String actor,
                                         Map<String, Object> before,
                                         Map<String, Object> after, String reason) {
        writeEvent(AGG_COSTCENTER, c.getId(), EVENT_COSTCENTER_CHANGED,
                payload(c.getId(), c.getTenantId(), kind, actor, before, after, reason));
    }

    @Override
    public void publishBusinessPartnerChanged(BusinessPartner b, ChangeKind kind, String actor,
                                              Map<String, Object> before,
                                              Map<String, Object> after, String reason) {
        writeEvent(AGG_BUSINESSPARTNER, b.getId(), EVENT_BUSINESSPARTNER_CHANGED,
                payload(b.getId(), b.getTenantId(), kind, actor, before, after, reason));
    }

    /**
     * Common masterdata payload. Copied VERBATIM from the v1
     * {@code MasterdataEventPublisher.payload} — {@code before}/{@code after}/
     * {@code reason} are put UNCONDITIONALLY (a {@code null} serialises as JSON
     * {@code null}), preserving the exact v1 wire.
     */
    private static Map<String, Object> payload(String aggregateId, String tenantId,
                                               ChangeKind kind, String actor,
                                               Map<String, Object> before,
                                               Map<String, Object> after, String reason) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("aggregateId", aggregateId);
        p.put("changeKind", kind.name());
        p.put("tenantId", tenantId);
        p.put("occurredAt", Instant.now().toString());
        p.put("actor", actor);
        p.put("before", before);
        p.put("after", after);
        p.put("reason", reason);
        return p;
    }

    /**
     * Wrap {@code payload} in the canonical 7-field envelope (preserved from the
     * v1 {@code BaseEventPublisher} path), serialise it, and persist a pending
     * {@code masterdata_outbox} row in the caller's transaction. The generated
     * {@link UuidV7} doubles as the envelope {@code eventId} and the row PK;
     * {@code partition_key = aggregateId} (the v1 Kafka key).
     */
    private void writeEvent(String aggregateType, String aggregateId,
                            String eventType, Map<String, Object> payload) {
        UUID eventId = UuidV7.randomUuid();
        Instant occurredAt = Instant.now(clock);

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId.toString());
        envelope.put("eventType", eventType);
        envelope.put("source", SOURCE);
        envelope.put("occurredAt", occurredAt.toString());
        envelope.put("schemaVersion", SCHEMA_VERSION);
        envelope.put("partitionKey", aggregateId);
        envelope.put("payload", payload);

        String json;
        try {
            json = objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "failed to serialise " + eventType + " outbox envelope", e);
        }

        outboxRepository.save(MasterdataOutboxJpaEntity.create(
                eventId, aggregateType, aggregateId, eventType,
                json, aggregateId, occurredAt));
    }
}
