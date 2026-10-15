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

    /** Publishes {@code tenant.created} with {@code status = ACTIVE} (the pre-773 shape). */
    default void publishTenantCreated(String tenantId, String displayName, String tenantType,
                                      String operatorId, Instant createdAt) {
        publishTenantCreated(tenantId, displayName, tenantType, "ACTIVE", operatorId, createdAt);
    }

    /**
     * Publishes {@code tenant.created} after a successful POST /api/admin/tenants — TASK-MONO-773 S4: {@code status}
     * is the tenant's real birth status ({@code PENDING} on the {@code SUPER_ADMIN} path).
     */
    void publishTenantCreated(String tenantId, String displayName, String tenantType, String status,
                              String operatorId, Instant createdAt);

    /** Publishes {@code tenant.suspended} for an ACTIVE → SUSPENDED transition (the pre-773 shape). */
    default void publishTenantSuspended(String tenantId, String operatorId,
                                        String reason, Instant suspendedAt) {
        publishTenantSuspended(tenantId, "ACTIVE", operatorId, reason, suspendedAt);
    }

    /**
     * Publishes {@code tenant.suspended} — TASK-MONO-773 S4: {@code previousStatus} is the status the tenant really
     * left ({@code ACTIVE}, or {@code PENDING} for the OD-2 «정리»).
     */
    void publishTenantSuspended(String tenantId, String previousStatus, String operatorId,
                                String reason, Instant suspendedAt);

    /**
     * TASK-MONO-773 S4 — {@code tenant.activated} ({@code PENDING → ACTIVE}). {@code trigger} =
     * {@code FIRST_ADMIN_ACCEPTED} (the first admin accepted) | {@code OPERATOR_COMPLETED} (the {@code SUPER_ADMIN}
     * recovery PATCH). No {@code reason} field (tenant-events.md § tenant.activated).
     */
    void publishTenantActivated(String tenantId, String trigger, String operatorId, Instant activatedAt);

    /** Publishes {@code tenant.reactivated} when status transitions SUSPENDED → ACTIVE. */
    void publishTenantReactivated(String tenantId, String operatorId,
                                  String reason, Instant reactivatedAt);

    /** Publishes {@code tenant.updated} when displayName changes. */
    void publishTenantUpdated(String tenantId, String displayNameFrom, String displayNameTo,
                              String operatorId, Instant updatedAt);
}
