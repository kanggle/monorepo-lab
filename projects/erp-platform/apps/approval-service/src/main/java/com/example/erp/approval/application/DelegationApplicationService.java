package com.example.erp.approval.application;

import com.example.common.id.UuidV7;
import com.example.erp.approval.application.command.Commands.CreateDelegationCommand;
import com.example.erp.approval.application.command.Commands.RevokeDelegationCommand;
import com.example.erp.approval.application.event.ApprovalEventPublisher;
import com.example.erp.approval.application.port.outbound.ClockPort;
import com.example.erp.approval.application.view.DelegationGrantView;
import com.example.erp.approval.domain.audit.ApprovalAuditLog;
import com.example.erp.approval.domain.audit.ApprovalAuditLogRepository;
import com.example.erp.approval.domain.delegation.DelegationGrant;
import com.example.erp.approval.domain.delegation.DelegationGrantRepository;
import com.example.erp.approval.domain.error.ApprovalErrors.DelegationNotFoundException;
import com.example.erp.approval.domain.error.ApprovalErrors.PermissionDeniedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Delegation grant lifecycle use cases (TASK-ERP-BE-013, 대결/위임). Each mutation
 * is one {@code @Transactional} boundary: the grant state change + an immutable
 * audit row (L131) commit atomically; create also writes the
 * {@code erp.approval.delegated.v1} outbox event (A7). Audit-fail-closed (A10).
 *
 * <p>Create / revoke require {@code erp.write} (own grants / operator); list
 * requires {@code erp.read}. The delegator A of a created grant = the caller's
 * {@code sub} (a caller delegates their OWN approver authority); an operator may
 * also create on behalf via the operator role.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DelegationApplicationService {

    private final DelegationGrantRepository delegationGrantRepository;
    private final ApprovalAuditLogRepository auditLogRepository;
    private final ApprovalEventPublisher eventPublisher;
    private final ClockPort clock;

    @Transactional
    public DelegationGrantView createDelegation(CreateDelegationCommand cmd) {
        ActorContext actor = cmd.actor();
        authorizeWrite(actor);
        Instant now = clock.now();

        // Delegator A = the caller's sub (delegating their own approver authority).
        // Domain factory enforces self-delegation + invalid-window → DELEGATION_INVALID.
        String grantId = "dgr-" + UuidV7.randomString();
        DelegationGrant grant = DelegationGrant.create(
                grantId, actor.tenantId(), actor.actorId(), cmd.delegateId(),
                cmd.validFrom(), cmd.validTo(), cmd.reason(), actor.actorId(), now);
        DelegationGrant saved = delegationGrantRepository.save(grant);

        // Immutable audit row (L131) — before = none, after = ACTIVE — same Tx.
        appendAudit(saved, "approval.delegation.created", actor.actorId(), null,
                "ACTIVE", cmd.reason(), now);
        // delegated.v1 outbox event (producer-only forward interface).
        eventPublisher.publishDelegated(saved, actor.actorId());
        return DelegationGrantView.from(saved);
    }

    @Transactional
    public DelegationGrantView revokeDelegation(RevokeDelegationCommand cmd) {
        ActorContext actor = cmd.actor();
        authorizeWrite(actor);
        Instant now = clock.now();

        DelegationGrant grant = delegationGrantRepository.findById(cmd.id(), actor.tenantId())
                .orElseThrow(() -> new DelegationNotFoundException(
                        "delegation grant not found: " + cmd.id()));

        // Idempotent — a no-op repeat revoke audits nothing new (the grant is
        // already REVOKED; the first revoke captured the audit). No event on revoke.
        boolean changed = grant.revoke(actor.actorId(), now);
        if (changed) {
            DelegationGrant saved = delegationGrantRepository.save(grant);
            appendAudit(saved, "approval.delegation.revoked", actor.actorId(),
                    "ACTIVE", "REVOKED", cmd.reason(), now);
            return DelegationGrantView.from(saved);
        }
        return DelegationGrantView.from(grant);
    }

    @Transactional(readOnly = true)
    public List<DelegationGrantView> listDelegations(ActorContext actor, DelegationRole role) {
        authorizeRead(actor);
        List<DelegationGrant> grants;
        if (role == DelegationRole.DELEGATOR) {
            grants = delegationGrantRepository.findByDelegator(actor.actorId(), actor.tenantId());
        } else if (role == DelegationRole.DELEGATE) {
            grants = delegationGrantRepository.findByDelegate(actor.actorId(), actor.tenantId());
        } else {
            grants = delegationGrantRepository.findByDelegatorOrDelegate(
                    actor.actorId(), actor.tenantId());
        }
        return grants.stream().map(DelegationGrantView::from).toList();
    }

    /** Optional {@code ?role=DELEGATOR|DELEGATE} list filter (null = both). */
    public enum DelegationRole { DELEGATOR, DELEGATE }

    public static DelegationRole parseRole(String role) {
        if (role == null || role.isBlank()) {
            return null;
        }
        return DelegationRole.valueOf(role.trim().toUpperCase());
    }

    // ---- helpers ----

    private void appendAudit(DelegationGrant grant, String action, String actor,
                             String before, String after, String reason, Instant now) {
        auditLogRepository.append(ApprovalAuditLog.of(
                "evt-" + UuidV7.randomString(), grant.getTenantId(),
                ApprovalEventPublisher.DELEGATION_AGGREGATE_TYPE, grant.getId(),
                action, actor,
                before == null ? null : "{\"status\":\"" + before + "\"}",
                "{\"status\":\"" + after + "\"}", reason, now));
    }

    private void authorizeWrite(ActorContext actor) {
        // WRITE: scope or operator (entitlement NEVER widens a write — mirrors
        // ApprovalApplicationService.authorizeWrite).
        if (actor.isOperator()
                || actor.hasScope("erp.write")
                || actor.hasScope("erp.approval.create")
                || actor.hasScope("erp.approval.approve")) {
            return;
        }
        throw new PermissionDeniedException("erp.write scope required for delegation mutation");
    }

    private void authorizeRead(ActorContext actor) {
        // READ dual-accept: scope OR operator OR entitlement-trust (ADR-MONO-019).
        if (actor.isOperator()
                || actor.hasScope("erp.read")
                || actor.hasScope("erp.write")
                || actor.hasScope("erp.approval.create")
                || actor.hasScope("erp.approval.approve")
                || actor.isEntitledTo("erp")) {
            return;
        }
        throw new PermissionDeniedException("erp.read scope required for delegation list");
    }
}
