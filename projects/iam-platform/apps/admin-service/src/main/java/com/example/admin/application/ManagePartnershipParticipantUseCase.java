package com.example.admin.application;

import com.example.admin.application.event.PartnershipEventPublisher;
import com.example.admin.application.exception.OperatorNotFoundException;
import com.example.admin.application.exception.ParticipantNotFoundException;
import com.example.admin.application.exception.ParticipantNotOwnOperatorException;
import com.example.admin.application.exception.ParticipantScopeExceedsDelegationException;
import com.example.admin.application.exception.PartnershipNotFoundException;
import com.example.admin.application.exception.PartnershipScopeDeniedException;
import com.example.admin.application.exception.PartnershipTransitionInvalidException;
import com.example.admin.application.exception.TenantScopeDeniedException;
import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.application.port.TenantPartnershipPort;
import com.example.admin.application.port.TenantPartnershipPort.PartnershipView;
import com.example.admin.domain.rbac.PartnershipStatus;
import com.example.admin.domain.rbac.Permission;
import com.example.admin.domain.rbac.ScopeSet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * TASK-BE-477 / ADR-MONO-045 D4/D6 — partner-side participant management: the partner
 * tenant B assigns/removes its OWN operators as participants of an ACTIVE partnership.
 *
 * <p>This is the offboarding fix (D6): because B governs its own people, B removing a
 * participant (or offboarding the employee via the normal operator lifecycle)
 * automatically removes that person's host-reach at the next assume-tenant request —
 * the host (A) never tracks B's staffing. Gated by {@code partnership.manage} + the D2
 * {@link TenantScopeGuard} (target = partner tenant); host may NOT name individual
 * B-people (D4-B rejected). Reason-gated + audited + outbox event, same tx.
 */
@Service
@RequiredArgsConstructor
public class ManagePartnershipParticipantUseCase {

    private final TenantPartnershipPort partnershipPort;
    private final TenantScopeGuard tenantScopeGuard;
    private final AdminActionAuditor auditor;
    private final PartnershipEventPublisher eventPublisher;
    private final AdminOperatorPort operatorPort;

    /**
     * Assign {@code operatorPublicId} (a partner-owned operator) as a participant.
     *
     * @param participantScope optional narrowing within {@code delegatedScope}
     *                         ({@code null} ⟺ the whole {@code delegatedScope}).
     */
    @Transactional
    public ParticipantResult addParticipant(String partnershipId, String operatorPublicId,
                                            String actingTenantId, List<String> scopeDomains,
                                            List<String> scopeRoles, OperatorContext actor,
                                            String reason) {
        requirePartnershipScope(actor, actingTenantId, ActionCode.PARTNERSHIP_PARTICIPANT_ADD);
        PartnershipView p = load(partnershipId);
        requirePartnerSide(p, actingTenantId);

        if (p.status() != PartnershipStatus.ACTIVE) {
            throw new PartnershipTransitionInvalidException(
                    "Participants can only be assigned to an ACTIVE partnership (status=" + p.status() + ")");
        }

        AdminOperatorPort.OperatorView operator = operatorPort.findByOperatorId(operatorPublicId)
                .orElseThrow(() -> new OperatorNotFoundException(
                        "Operator not found: " + operatorPublicId));

        // D4: B may only assign its OWN operators — home tenant must equal partner.
        if (!p.partnerTenantId().equals(operator.tenantId())) {
            throw new ParticipantNotOwnOperatorException(
                    "Operator home tenant does not match the partner tenant");
        }

        ScopeSet participantScope = scopeDomains == null && scopeRoles == null
                ? null : ScopeSet.of(scopeDomains, scopeRoles);
        // participant_scope ⊆ delegated_scope
        if (participantScope != null && !participantScope.isSubsetOf(p.delegatedScope())) {
            throw new ParticipantScopeExceedsDelegationException(
                    "participantScope exceeds the partnership's delegatedScope");
        }

        Long actorInternalId = operatorPort.resolveActorInternalId(actorId(actor));
        Instant now = Instant.now();
        partnershipPort.addParticipant(p.internalId(), operator.internalId(),
                participantScope, actorInternalId, now);

        recordAudit(ActionCode.PARTNERSHIP_PARTICIPANT_ADD, partnershipId, operator.operatorId(),
                p.partnerTenantId(), actor, reason);
        eventPublisher.publishParticipantAdded(partnershipId, p.hostTenantId(), p.partnerTenantId(),
                operator.operatorId(), participantScope, actorId(actor), now);
        return new ParticipantResult(partnershipId, operator.operatorId(), participantScope, now);
    }

    /** Remove a participant (D6 individual offboarding). Host-reach derivation gone next request. */
    @Transactional
    public void removeParticipant(String partnershipId, String operatorPublicId,
                                  String actingTenantId, OperatorContext actor, String reason) {
        requirePartnershipScope(actor, actingTenantId, ActionCode.PARTNERSHIP_PARTICIPANT_REMOVE);
        PartnershipView p = load(partnershipId);
        requirePartnerSide(p, actingTenantId);

        AdminOperatorPort.OperatorView operator = operatorPort.findByOperatorId(operatorPublicId)
                .orElseThrow(() -> new OperatorNotFoundException(
                        "Operator not found: " + operatorPublicId));

        if (partnershipPort.findParticipant(p.internalId(), operator.internalId()).isEmpty()) {
            throw new ParticipantNotFoundException(
                    "No participant binding for operator=" + operatorPublicId + " in partnership=" + partnershipId);
        }

        partnershipPort.removeParticipant(p.internalId(), operator.internalId());

        Instant now = Instant.now();
        recordAudit(ActionCode.PARTNERSHIP_PARTICIPANT_REMOVE, partnershipId, operator.operatorId(),
                p.partnerTenantId(), actor, reason);
        eventPublisher.publishParticipantRemoved(partnershipId, p.hostTenantId(), p.partnerTenantId(),
                operator.operatorId(), actorId(actor), now);
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private PartnershipView load(String partnershipId) {
        return partnershipPort.findByPartnershipId(partnershipId)
                .orElseThrow(() -> new PartnershipNotFoundException(
                        "Partnership not found: " + partnershipId));
    }

    private void requirePartnerSide(PartnershipView p, String actingTenantId) {
        if (!actingTenantId.equals(p.partnerTenantId())) {
            // Enumeration-safe: only the partner side manages participants.
            throw new PartnershipNotFoundException("Partnership not found for the acting tenant");
        }
    }

    private void requirePartnershipScope(OperatorContext actor, String actingTenantId, ActionCode code) {
        try {
            tenantScopeGuard.requireTenantInScope(
                    actor, Permission.PARTNERSHIP_MANAGE, actingTenantId, code);
        } catch (TenantScopeDeniedException e) {
            throw new PartnershipScopeDeniedException(e.getMessage());
        }
    }

    private void recordAudit(ActionCode code, String partnershipId, String operatorId,
                             String targetTenantId, OperatorContext actor, String reason) {
        Instant now = Instant.now();
        auditor.recordWithPermission(
                new AdminActionAuditor.AuditRecord(
                        UUID.randomUUID().toString(),
                        code,
                        actor,
                        "PARTNERSHIP",
                        operatorId,
                        AuditReasons.normalize(reason),
                        null,
                        "participant:" + code.name() + ":" + partnershipId + ":" + operatorId + ":" + now.toEpochMilli(),
                        Outcome.SUCCESS,
                        null,
                        now,
                        now,
                        targetTenantId),
                Permission.PARTNERSHIP_MANAGE);
    }

    private static String actorId(OperatorContext actor) {
        return actor == null ? null : actor.operatorId();
    }

    /** Result of a participant assignment. {@code participantScope} may be {@code null}. */
    public record ParticipantResult(
            String partnershipId,
            String operatorId,
            ScopeSet participantScope,
            Instant assignedAt
    ) {}
}
