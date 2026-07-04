package com.example.admin.application.port;

import com.example.admin.domain.rbac.PartnershipStatus;
import com.example.admin.domain.rbac.ScopeSet;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * TASK-BE-477 / ADR-MONO-045 D1/D4 — application-layer port over the
 * {@code tenant_partnership} + {@code tenant_partnership_participant} tables. Keeps
 * the JPA entities/repositories out of the application layer's import graph (same
 * pattern as {@link OperatorTenantAssignmentPort} / {@link AdminOperatorPort}).
 *
 * <p>Consumed by {@code PartnershipManagementUseCase} /
 * {@code ManagePartnershipParticipantUseCase} (the management surface) and by
 * {@code OperatorAssignmentCheckUseCase} (the assume-tenant cross-org confinement
 * read path — {@link #findActivePartnership} + {@link #findParticipant}).
 */
public interface TenantPartnershipPort {

    // ---------- Cross-org confinement read path (assume-tenant) ----------

    /**
     * The ACTIVE partnership for the ordered {@code (host, partner)} pair, or empty
     * when none exists / it is PENDING / SUSPENDED / TERMINATED (→ derives 0 reach).
     */
    Optional<PartnershipView> findActivePartnership(String hostTenantId, String partnerTenantId);

    /**
     * The participant binding for {@code (partnershipInternalId, operatorInternalId)},
     * or empty when the operator is not a participant (→ derives 0 reach).
     */
    Optional<ParticipantView> findParticipant(long partnershipInternalId, long operatorInternalId);

    // ---------- Management surface (lifecycle) ----------

    /** Look up a partnership by its external {@code partnership_id} UUID. */
    Optional<PartnershipView> findByPartnershipId(String partnershipId);

    /** Whether ANY partnership (any status) exists for the ordered {@code (host, partner)} pair. */
    boolean pairExists(String hostTenantId, String partnerTenantId);

    /**
     * Persist a new PENDING partnership (invite). Uses {@code saveAndFlush} (BE-335) so
     * the {@code uk_tenant_partnership_pair} collision surfaces synchronously as a
     * {@link org.springframework.dao.DataIntegrityViolationException} the caller maps
     * to {@code PARTNERSHIP_ALREADY_EXISTS}.
     *
     * @return the persisted view (populated {@code internalId}, timestamps).
     */
    PartnershipView createPending(NewPartnership row);

    /**
     * Apply a lifecycle transition to the partnership identified by its internal id:
     * set {@code status} and the relevant timestamp / {@code accepted_by}. Uses
     * {@code saveAndFlush} (BE-335).
     */
    void applyTransition(long partnershipInternalId, PartnershipStatus newStatus,
                         Long actorInternalId, Instant at);

    /**
     * All partnerships where {@code tenantId} is the host OR the partner, optionally
     * filtered by {@code roleFilter} ({@code "host"}/{@code "partner"}/{@code null}=both)
     * and {@code statusFilter} ({@code null}=all), paginated ({@code createdAt DESC}).
     */
    PartnershipPage listForTenant(String tenantId, String roleFilter, PartnershipStatus statusFilter,
                                  int page, int size);

    /** Count of participants currently bound to the partnership (audit legibility). */
    int countParticipants(long partnershipInternalId);

    // ---------- Participant surface ----------

    /** Persist a participant binding. {@code saveAndFlush} (BE-335). */
    void addParticipant(long partnershipInternalId, long operatorInternalId,
                        ScopeSet participantScope, Long assignedByInternalId, Instant at);

    /** Remove the participant binding. No-op when absent (caller's 404 gate is authoritative). */
    void removeParticipant(long partnershipInternalId, long operatorInternalId);

    // ---------- Projections ----------

    /**
     * Immutable projection of a {@code tenant_partnership} row.
     *
     * @param delegatedScope never {@code null} (NOT NULL column).
     */
    record PartnershipView(
            long internalId,
            String partnershipId,
            String hostTenantId,
            String partnerTenantId,
            PartnershipStatus status,
            ScopeSet delegatedScope,
            Long invitedBy,
            Long acceptedBy,
            Instant invitedAt,
            Instant acceptedAt,
            Instant terminatedAt
    ) {}

    /**
     * Immutable projection of a {@code tenant_partnership_participant} row.
     *
     * @param participantScope {@code null} ⟺ the whole {@code delegatedScope}
     *                         (net-zero narrowing default).
     */
    record ParticipantView(
            long partnershipInternalId,
            long operatorInternalId,
            ScopeSet participantScope,
            Instant assignedAt,
            Long assignedBy
    ) {}

    /** Value used to INSERT a new PENDING partnership. */
    record NewPartnership(
            String partnershipId,
            String hostTenantId,
            String partnerTenantId,
            ScopeSet delegatedScope,
            Long invitedByInternalId,
            Instant invitedAt
    ) {}

    /** Page projection for the list surface. */
    record PartnershipPage(
            List<PartnershipView> content,
            long totalElements,
            int page,
            int size,
            int totalPages
    ) {}
}
