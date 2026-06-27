package com.example.admin.application.event;

import java.time.Instant;

/**
 * Port for tenant lifecycle event publishing (TASK-BE-250; TASK-BE-452 — outbox
 * v1 → v2). Per {@code specs/contracts/events/tenant-events.md}.
 *
 * <p>Previously a concrete {@code BaseEventPublisher} subclass writing to the shared
 * lib {@code outbox} via {@code OutboxWriter.saveEvent}. Unlike
 * {@link AdminEventPublisher} (flat), each {@code tenant.*} method SELF-BUILT the full
 * canonical 7-field envelope {@code {eventId, eventType, source, occurredAt,
 * schemaVersion, partitionKey, payload}} and passed THAT as the {@code saveEvent}
 * payload — so the on-wire Kafka value is a full envelope. The impl
 * {@link com.example.admin.infrastructure.outbox.OutboxTenantEventPublisher}
 * reproduces those EXACT bytes (no double-wrap) and persists an {@code admin_outbox}
 * row (the SAME table as admin.action.performed) driven by the v2 relay.
 *
 * <p>Partition key = tenantId — ensures ordering within a single tenant's lifecycle.
 * The caller invokes the correct method within the same DB transaction as the audit
 * row INSERT (outbox pattern T3).
 */
public interface TenantEventPublisher {

    /** Publishes {@code tenant.created} after a successful POST /api/admin/tenants. */
    void publishTenantCreated(String tenantId, String displayName, String tenantType,
                              String operatorId, Instant createdAt);

    /** Publishes {@code tenant.suspended} when status transitions ACTIVE → SUSPENDED. */
    void publishTenantSuspended(String tenantId, String operatorId,
                                String reason, Instant suspendedAt);

    /** Publishes {@code tenant.reactivated} when status transitions SUSPENDED → ACTIVE. */
    void publishTenantReactivated(String tenantId, String operatorId,
                                  String reason, Instant reactivatedAt);

    /** Publishes {@code tenant.updated} when displayName changes. */
    void publishTenantUpdated(String tenantId, String displayNameFrom, String displayNameTo,
                              String operatorId, Instant updatedAt);
}
