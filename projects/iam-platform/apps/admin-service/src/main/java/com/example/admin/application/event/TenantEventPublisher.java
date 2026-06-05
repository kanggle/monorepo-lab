package com.example.admin.application.event;

import com.example.common.id.UuidV7;
import com.example.messaging.event.BaseEventPublisher;
import com.example.messaging.outbox.OutboxWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * TASK-BE-250: Publishes tenant lifecycle outbox events per
 * {@code specs/contracts/events/tenant-events.md}.
 *
 * <p>All four event types share the same actor structure and envelope format.
 * The caller is responsible for invoking the correct method within the same DB
 * transaction as the audit row INSERT (outbox pattern T3).
 *
 * <p>Partition key = tenantId — ensures ordering within a single tenant's
 * lifecycle events per the consumer rules in tenant-events.md.
 */
@Component
public class TenantEventPublisher extends BaseEventPublisher {

    private static final String AGGREGATE_TYPE = "Tenant";

    public TenantEventPublisher(OutboxWriter outboxWriter, ObjectMapper objectMapper) {
        super(outboxWriter, objectMapper);
    }

    /**
     * Publishes {@code tenant.created} after a successful POST /api/admin/tenants.
     */
    public void publishTenantCreated(String tenantId, String displayName, String tenantType,
                                     String operatorId, Instant createdAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", UuidV7.randomString());
        payload.put("eventType", "tenant.created");
        payload.put("source", "admin-service");
        payload.put("occurredAt", createdAt.toString());
        payload.put("schemaVersion", 1);
        payload.put("partitionKey", tenantId);

        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("tenantId", tenantId);
        inner.put("displayName", displayName);
        inner.put("tenantType", tenantType);
        inner.put("status", "ACTIVE");
        inner.put("actor", actorOf(operatorId));
        inner.put("createdAt", createdAt.toString());
        payload.put("payload", inner);

        saveEvent(AGGREGATE_TYPE, tenantId, "tenant.created", payload);
    }

    /**
     * Publishes {@code tenant.suspended} when status transitions ACTIVE → SUSPENDED.
     */
    public void publishTenantSuspended(String tenantId, String operatorId,
                                       String reason, Instant suspendedAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", UuidV7.randomString());
        payload.put("eventType", "tenant.suspended");
        payload.put("source", "admin-service");
        payload.put("occurredAt", suspendedAt.toString());
        payload.put("schemaVersion", 1);
        payload.put("partitionKey", tenantId);

        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("tenantId", tenantId);
        inner.put("previousStatus", "ACTIVE");
        inner.put("currentStatus", "SUSPENDED");
        inner.put("reason", reason);
        inner.put("actor", actorOf(operatorId));
        inner.put("suspendedAt", suspendedAt.toString());
        payload.put("payload", inner);

        saveEvent(AGGREGATE_TYPE, tenantId, "tenant.suspended", payload);
    }

    /**
     * Publishes {@code tenant.reactivated} when status transitions SUSPENDED → ACTIVE.
     */
    public void publishTenantReactivated(String tenantId, String operatorId,
                                         String reason, Instant reactivatedAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", UuidV7.randomString());
        payload.put("eventType", "tenant.reactivated");
        payload.put("source", "admin-service");
        payload.put("occurredAt", reactivatedAt.toString());
        payload.put("schemaVersion", 1);
        payload.put("partitionKey", tenantId);

        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("tenantId", tenantId);
        inner.put("previousStatus", "SUSPENDED");
        inner.put("currentStatus", "ACTIVE");
        inner.put("reason", reason);
        inner.put("actor", actorOf(operatorId));
        inner.put("reactivatedAt", reactivatedAt.toString());
        payload.put("payload", inner);

        saveEvent(AGGREGATE_TYPE, tenantId, "tenant.reactivated", payload);
    }

    /**
     * Publishes {@code tenant.updated} when displayName changes.
     * Status changes emit suspend/reactivate events instead.
     */
    public void publishTenantUpdated(String tenantId, String displayNameFrom, String displayNameTo,
                                     String operatorId, Instant updatedAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", UuidV7.randomString());
        payload.put("eventType", "tenant.updated");
        payload.put("source", "admin-service");
        payload.put("occurredAt", updatedAt.toString());
        payload.put("schemaVersion", 1);
        payload.put("partitionKey", tenantId);

        Map<String, Object> changes = new LinkedHashMap<>();
        Map<String, Object> displayNameChange = new LinkedHashMap<>();
        displayNameChange.put("from", displayNameFrom);
        displayNameChange.put("to", displayNameTo);
        changes.put("displayName", displayNameChange);

        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("tenantId", tenantId);
        inner.put("changes", changes);
        inner.put("actor", actorOf(operatorId));
        inner.put("updatedAt", updatedAt.toString());
        payload.put("payload", inner);

        saveEvent(AGGREGATE_TYPE, tenantId, "tenant.updated", payload);
    }

    private static Map<String, Object> actorOf(String operatorId) {
        Map<String, Object> actor = new LinkedHashMap<>();
        actor.put("type", "operator");
        actor.put("id", operatorId);
        return actor;
    }
}
