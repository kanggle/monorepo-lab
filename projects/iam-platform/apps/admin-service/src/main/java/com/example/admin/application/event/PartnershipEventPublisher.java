package com.example.admin.application.event;

import com.example.admin.domain.rbac.ScopeSet;

import java.time.Instant;

/**
 * TASK-BE-477 / ADR-MONO-045 — port for cross-org partnership lifecycle event
 * publishing. Per {@code specs/contracts/events/partnership-events.md} (7 events).
 *
 * <p>Mirrors {@link TenantEventPublisher}: each method SELF-BUILDS the full canonical
 * 7-field envelope {@code {eventId, eventType, source, occurredAt, schemaVersion,
 * partitionKey, payload}} and the impl
 * {@link com.example.admin.infrastructure.outbox.OutboxPartnershipEventPublisher}
 * persists an {@code admin_outbox} row (the SAME table as admin.action.performed)
 * driven by the v2 relay.
 *
 * <p><b>Partition key = partnershipId</b> — ensures ordering within a single
 * partnership's lifecycle (invite → accept → … → terminate). The caller invokes the
 * matching method within the same DB transaction as the audit row INSERT (outbox
 * pattern T3).
 */
public interface PartnershipEventPublisher {

    /** {@code partnership.invited} — host invites (→ PENDING). */
    void publishInvited(String partnershipId, String hostTenantId, String partnerTenantId,
                        ScopeSet delegatedScope, String actorOperatorId, Instant invitedAt);

    /** {@code partnership.accepted} — partner accepts (PENDING → ACTIVE). */
    void publishAccepted(String partnershipId, String hostTenantId, String partnerTenantId,
                         String actorOperatorId, Instant acceptedAt);

    /** {@code partnership.suspended} — ACTIVE → SUSPENDED (either party). */
    void publishSuspended(String partnershipId, String hostTenantId, String partnerTenantId,
                          String reason, String actorOperatorId, Instant suspendedAt);

    /** {@code partnership.reactivated} — SUSPENDED → ACTIVE (either party). */
    void publishReactivated(String partnershipId, String hostTenantId, String partnerTenantId,
                            String reason, String actorOperatorId, Instant reactivatedAt);

    /**
     * {@code partnership.terminated} — → TERMINATED (either party). ONE-SHOT cascade
     * event: a single event even for N participants (D6). {@code previousStatus}
     * may be PENDING/ACTIVE/SUSPENDED.
     */
    void publishTerminated(String partnershipId, String hostTenantId, String partnerTenantId,
                           String previousStatus, String reason, int participantCountAtTermination,
                           String actorOperatorId, Instant terminatedAt);

    /** {@code partnership.participant_added} — partner assigns its own operator (D4). */
    void publishParticipantAdded(String partnershipId, String hostTenantId, String partnerTenantId,
                                 String operatorId, ScopeSet participantScope,
                                 String actorOperatorId, Instant assignedAt);

    /** {@code partnership.participant_removed} — participant offboarded (D6 individual). */
    void publishParticipantRemoved(String partnershipId, String hostTenantId, String partnerTenantId,
                                   String operatorId, String actorOperatorId, Instant removedAt);
}
