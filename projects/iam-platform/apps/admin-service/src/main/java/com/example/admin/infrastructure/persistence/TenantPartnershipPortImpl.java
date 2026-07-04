package com.example.admin.infrastructure.persistence;

import com.example.admin.application.port.TenantPartnershipPort;
import com.example.admin.domain.rbac.PartnershipStatus;
import com.example.admin.domain.rbac.ScopeSet;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * TASK-BE-477 / ADR-MONO-045 D1/D4 — JPA-backed {@link TenantPartnershipPort}. Keeps
 * the {@code tenant_partnership*} JPA types out of the application layer. Writes use
 * {@code saveAndFlush} (BE-335) so dirty INSERT/UPDATE flush WITHIN the request tx
 * (and unique-constraint collisions surface synchronously).
 */
@Component
@RequiredArgsConstructor
public class TenantPartnershipPortImpl implements TenantPartnershipPort {

    private final TenantPartnershipJpaRepository partnershipRepository;
    private final TenantPartnershipParticipantJpaRepository participantRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<PartnershipView> findActivePartnership(String hostTenantId, String partnerTenantId) {
        if (hostTenantId == null || partnerTenantId == null) {
            return Optional.empty();
        }
        return partnershipRepository
                .findByHostTenantIdAndPartnerTenantIdAndStatus(
                        hostTenantId, partnerTenantId, PartnershipStatus.ACTIVE.name())
                .map(TenantPartnershipPortImpl::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ParticipantView> findParticipant(long partnershipInternalId, long operatorInternalId) {
        return participantRepository
                .findByPartnershipIdAndOperatorId(partnershipInternalId, operatorInternalId)
                .map(TenantPartnershipPortImpl::toParticipantView);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PartnershipView> findByPartnershipId(String partnershipId) {
        if (partnershipId == null || partnershipId.isBlank()) {
            return Optional.empty();
        }
        return partnershipRepository.findByPartnershipId(partnershipId)
                .map(TenantPartnershipPortImpl::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean pairExists(String hostTenantId, String partnerTenantId) {
        return partnershipRepository.existsByHostTenantIdAndPartnerTenantId(hostTenantId, partnerTenantId);
    }

    @Override
    @Transactional
    public PartnershipView createPending(NewPartnership row) {
        TenantPartnershipJpaEntity saved = partnershipRepository.saveAndFlush(
                TenantPartnershipJpaEntity.createPending(
                        row.partnershipId(), row.hostTenantId(), row.partnerTenantId(),
                        row.delegatedScope(), row.invitedByInternalId(), row.invitedAt()));
        return toView(saved);
    }

    @Override
    @Transactional
    public void applyTransition(long partnershipInternalId, PartnershipStatus newStatus,
                                Long actorInternalId, Instant at) {
        partnershipRepository.findById(partnershipInternalId).ifPresent(entity -> {
            entity.applyStatus(newStatus, actorInternalId, at);
            partnershipRepository.saveAndFlush(entity);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public PartnershipPage listForTenant(String tenantId, String roleFilter,
                                         PartnershipStatus statusFilter, int page, int size) {
        Page<TenantPartnershipJpaEntity> result = partnershipRepository.findForTenant(
                tenantId, roleFilter, statusFilter == null ? null : statusFilter.name(),
                PageRequest.of(page, size));
        List<PartnershipView> content = result.getContent().stream()
                .map(TenantPartnershipPortImpl::toView)
                .toList();
        return new PartnershipPage(content, result.getTotalElements(), page, size, result.getTotalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public int countParticipants(long partnershipInternalId) {
        return participantRepository.countByPartnershipId(partnershipInternalId);
    }

    @Override
    @Transactional
    public void addParticipant(long partnershipInternalId, long operatorInternalId,
                               ScopeSet participantScope, Long assignedByInternalId, Instant at) {
        // saveAndFlush merges on the composite PK, so a re-assign updates the scope
        // (idempotent-friendly) rather than throwing a PK violation.
        participantRepository.saveAndFlush(TenantPartnershipParticipantJpaEntity.create(
                partnershipInternalId, operatorInternalId, participantScope, assignedByInternalId, at));
    }

    @Override
    @Transactional
    public void removeParticipant(long partnershipInternalId, long operatorInternalId) {
        participantRepository
                .findByPartnershipIdAndOperatorId(partnershipInternalId, operatorInternalId)
                .ifPresent(participantRepository::delete);
    }

    // ── mappers ─────────────────────────────────────────────────────────────

    private static PartnershipView toView(TenantPartnershipJpaEntity e) {
        return new PartnershipView(
                e.getId(),
                e.getPartnershipId(),
                e.getHostTenantId(),
                e.getPartnerTenantId(),
                e.statusEnum(),
                e.delegatedScopeSet(),
                e.getInvitedBy(),
                e.getAcceptedBy(),
                e.getInvitedAt(),
                e.getAcceptedAt(),
                e.getTerminatedAt());
    }

    private static ParticipantView toParticipantView(TenantPartnershipParticipantJpaEntity e) {
        return new ParticipantView(
                e.getPartnershipId(),
                e.getOperatorId(),
                e.participantScopeSet(),
                e.getAssignedAt(),
                e.getAssignedBy());
    }
}
