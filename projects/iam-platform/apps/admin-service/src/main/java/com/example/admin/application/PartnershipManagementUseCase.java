package com.example.admin.application;

import com.example.admin.application.event.PartnershipEventPublisher;
import com.example.admin.application.exception.PartnershipAlreadyExistsException;
import com.example.admin.application.exception.PartnershipNotFoundException;
import com.example.admin.application.exception.PartnershipScopeDeniedException;
import com.example.admin.application.exception.PartnershipScopeInvalidException;
import com.example.admin.application.exception.PartnershipTransitionInvalidException;
import com.example.admin.application.exception.TenantScopeDeniedException;
import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.application.port.TenantPartnershipPort;
import com.example.admin.application.port.TenantPartnershipPort.PartnershipView;
import com.example.admin.domain.rbac.AdminOperator;
import com.example.admin.domain.rbac.PartnershipStatus;
import com.example.admin.domain.rbac.Permission;
import com.example.admin.domain.rbac.ScopeSet;
import com.example.common.id.UuidV7;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * TASK-BE-477 / ADR-MONO-045 D1/D2/D3/D6 — the cross-org partnership lifecycle
 * use-case: invite / accept / suspend / reactivate / terminate + list.
 *
 * <p>Every mutation:
 * <ol>
 *   <li>D2 confinement (step A): {@link TenantScopeGuard} on the acting-side tenant
 *       (the actor's {@code X-Tenant-Id}) for {@code partnership.manage} — a
 *       {@code TenantScopeDeniedException} is translated to
 *       {@link PartnershipScopeDeniedException} (403 {@code PARTNERSHIP_SCOPE_DENIED}).</li>
 *   <li>Side membership (step B): the acting tenant must be the required party
 *       (invite → host; accept/participant → partner; suspend/reactivate/terminate →
 *       either party) — else 404 {@code PARTNERSHIP_NOT_FOUND} (enumeration-safe).</li>
 *   <li>Business validation + state-machine guard ({@link PartnershipStatus}).</li>
 *   <li>Audit row ({@code recordWithPermission}) + outbox lifecycle event IN THE SAME
 *       transaction (outbox pattern T3).</li>
 * </ol>
 *
 * <p><b>The load-bearing invariant.</b> This surface changes only the partnership
 * RELATIONSHIP state. It never touches {@code AdminGrantScopeEvaluator} — a cross-org
 * actor has EMPTY admin scope in the host (403 on {@code /api/admin/**}). The DERIVED
 * domain-operating authority a partner operator gains is capped by a separate axis in
 * {@link OperatorAssignmentCheckUseCase} ({@code delegated_scope ∩ participant ∩
 * host-holds}), never widening admin scope.
 */
@Service
@RequiredArgsConstructor
public class PartnershipManagementUseCase {

    private static final Pattern TENANT_ID_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]{0,31}$");

    private final TenantPartnershipPort partnershipPort;
    private final TenantScopeGuard tenantScopeGuard;
    private final AdminActionAuditor auditor;
    private final PartnershipEventPublisher eventPublisher;
    private final AdminOperatorPort operatorPort;
    private final HostEntitledScopeResolver hostEntitledScopeResolver;

    // ── invite ────────────────────────────────────────────────────────────────

    /**
     * Host tenant {@code hostTenantId} (= {@code X-Tenant-Id}) invites
     * {@code partnerTenantId} with a bounded {@code delegatedScope} (→ PENDING).
     */
    @Transactional
    public PartnershipView invite(String hostTenantId, InvitePartnershipCommand cmd,
                                  OperatorContext actor, String reason) {
        requireHostNotPlatform(hostTenantId);
        requirePartnershipScope(actor, hostTenantId, ActionCode.PARTNERSHIP_INVITE);

        String partnerTenantId = cmd.partnerTenantId();
        validatePartnerTenant(hostTenantId, partnerTenantId);

        ScopeSet delegated = ScopeSet.of(cmd.domains(), cmd.roles());
        validateDelegatedScopeCap(hostTenantId, delegated);

        if (partnershipPort.pairExists(hostTenantId, partnerTenantId)) {
            throw new PartnershipAlreadyExistsException(
                    "Partnership already exists for host=" + hostTenantId + " partner=" + partnerTenantId);
        }

        Long actorInternalId = operatorPort.resolveActorInternalId(actorId(actor));
        String partnershipId = UuidV7.randomString();
        Instant now = Instant.now();

        PartnershipView created;
        try {
            created = partnershipPort.createPending(new TenantPartnershipPort.NewPartnership(
                    partnershipId, hostTenantId, partnerTenantId, delegated, actorInternalId, now));
        } catch (DataIntegrityViolationException race) {
            // uk_tenant_partnership_pair collision (concurrent invite).
            throw new PartnershipAlreadyExistsException(
                    "Partnership already exists for host=" + hostTenantId + " partner=" + partnerTenantId);
        }

        recordAudit(ActionCode.PARTNERSHIP_INVITE, created.partnershipId(),
                created.partnershipId(), partnerTenantId, actor, reason);
        eventPublisher.publishInvited(created.partnershipId(), hostTenantId, partnerTenantId,
                delegated, actorId(actor), created.invitedAt());
        return created;
    }

    // ── accept ─────────────────────────────────────────────────────────────────

    /** Partner tenant accepts the invite (PENDING → ACTIVE). Partner-only. */
    @Transactional
    public PartnershipView accept(String partnershipId, String actingTenantId,
                                  OperatorContext actor, String reason) {
        requirePartnershipScope(actor, actingTenantId, ActionCode.PARTNERSHIP_ACCEPT);
        PartnershipView p = load(partnershipId);
        requireSide(p, actingTenantId, Side.PARTNER);

        if (p.status().accept() == PartnershipStatus.Transition.INVALID) {
            throw new PartnershipTransitionInvalidException(
                    "Cannot accept a partnership in status " + p.status());
        }
        Long actorInternalId = operatorPort.resolveActorInternalId(actorId(actor));
        Instant now = Instant.now();
        partnershipPort.applyTransition(p.internalId(), PartnershipStatus.ACTIVE, actorInternalId, now);

        recordAudit(ActionCode.PARTNERSHIP_ACCEPT, partnershipId, partnershipId,
                p.partnerTenantId(), actor, reason);
        eventPublisher.publishAccepted(partnershipId, p.hostTenantId(), p.partnerTenantId(),
                actorId(actor), now);
        return reload(partnershipId);
    }

    // ── suspend / reactivate / terminate ────────────────────────────────────────

    /** ACTIVE → SUSPENDED (either party). SUSPENDED → no-op 200 (no event). */
    @Transactional
    public PartnershipView suspend(String partnershipId, String actingTenantId,
                                   OperatorContext actor, String reason) {
        return lifecycle(partnershipId, actingTenantId, actor, reason,
                ActionCode.PARTNERSHIP_SUSPEND, LifecycleKind.SUSPEND);
    }

    /** SUSPENDED → ACTIVE (either party). */
    @Transactional
    public PartnershipView reactivate(String partnershipId, String actingTenantId,
                                      OperatorContext actor, String reason) {
        return lifecycle(partnershipId, actingTenantId, actor, reason,
                ActionCode.PARTNERSHIP_REACTIVATE, LifecycleKind.REACTIVATE);
    }

    /** → TERMINATED (either party). TERMINATED → idempotent no-op 200. One-shot event. */
    @Transactional
    public PartnershipView terminate(String partnershipId, String actingTenantId,
                                     OperatorContext actor, String reason) {
        return lifecycle(partnershipId, actingTenantId, actor, reason,
                ActionCode.PARTNERSHIP_TERMINATE, LifecycleKind.TERMINATE);
    }

    private enum LifecycleKind { SUSPEND, REACTIVATE, TERMINATE }

    private PartnershipView lifecycle(String partnershipId, String actingTenantId,
                                      OperatorContext actor, String reason,
                                      ActionCode code, LifecycleKind kind) {
        requirePartnershipScope(actor, actingTenantId, code);
        PartnershipView p = load(partnershipId);
        requireSide(p, actingTenantId, Side.EITHER);

        PartnershipStatus.Transition t = switch (kind) {
            case SUSPEND -> p.status().suspend();
            case REACTIVATE -> p.status().reactivate();
            case TERMINATE -> p.status().terminate();
        };
        if (t == PartnershipStatus.Transition.INVALID) {
            throw new PartnershipTransitionInvalidException(
                    "Illegal " + kind + " from status " + p.status());
        }

        Instant now = Instant.now();
        Long actorInternalId = operatorPort.resolveActorInternalId(actorId(actor));

        if (t == PartnershipStatus.Transition.NO_OP) {
            // Idempotent same-state: 200, no event, no mutation. Still audited (audit-heavy).
            recordAudit(code, partnershipId, partnershipId, actingTenantId, actor, reason);
            return p;
        }

        // APPLIED
        PartnershipStatus previous = p.status();
        PartnershipStatus target = switch (kind) {
            case SUSPEND -> PartnershipStatus.SUSPENDED;
            case REACTIVATE -> PartnershipStatus.ACTIVE;
            case TERMINATE -> PartnershipStatus.TERMINATED;
        };
        int participantCount = kind == LifecycleKind.TERMINATE
                ? partnershipPort.countParticipants(p.internalId()) : 0;

        partnershipPort.applyTransition(p.internalId(), target, actorInternalId, now);
        recordAudit(code, partnershipId, partnershipId, actingTenantId, actor, reason);

        switch (kind) {
            case SUSPEND -> eventPublisher.publishSuspended(partnershipId, p.hostTenantId(),
                    p.partnerTenantId(), reason, actorId(actor), now);
            case REACTIVATE -> eventPublisher.publishReactivated(partnershipId, p.hostTenantId(),
                    p.partnerTenantId(), reason, actorId(actor), now);
            case TERMINATE -> eventPublisher.publishTerminated(partnershipId, p.hostTenantId(),
                    p.partnerTenantId(), previous.name(), reason, participantCount, actorId(actor), now);
        }
        return reload(partnershipId);
    }

    // ── list ─────────────────────────────────────────────────────────────────

    /**
     * Partnerships where {@code actingTenantId} is a party (host or partner), confined
     * to the acting tenant (D2 read parity). Blank acting tenant → empty page
     * (fail-closed, no leak).
     */
    @Transactional(readOnly = true)
    public TenantPartnershipPort.PartnershipPage list(String actingTenantId, String roleFilter,
                                                      String statusFilter, int page, int size) {
        if (actingTenantId == null || actingTenantId.isBlank()) {
            return new TenantPartnershipPort.PartnershipPage(List.of(), 0, page, size, 0);
        }
        PartnershipStatus status = parseStatusFilter(statusFilter);
        String role = (roleFilter == null || roleFilter.isBlank()) ? null : roleFilter.toLowerCase();
        return partnershipPort.listForTenant(actingTenantId, role, status, page, size);
    }

    /** Participant count for the list response's {@code participantCount} field. */
    @Transactional(readOnly = true)
    public int participantCount(long partnershipInternalId) {
        return partnershipPort.countParticipants(partnershipInternalId);
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private enum Side { HOST, PARTNER, EITHER }

    private PartnershipView load(String partnershipId) {
        return partnershipPort.findByPartnershipId(partnershipId)
                .orElseThrow(() -> new PartnershipNotFoundException(
                        "Partnership not found: " + partnershipId));
    }

    private PartnershipView reload(String partnershipId) {
        return partnershipPort.findByPartnershipId(partnershipId)
                .orElseThrow(() -> new PartnershipNotFoundException(
                        "Partnership not found after transition: " + partnershipId));
    }

    private void requireSide(PartnershipView p, String actingTenantId, Side side) {
        boolean ok = switch (side) {
            case HOST -> actingTenantId.equals(p.hostTenantId());
            case PARTNER -> actingTenantId.equals(p.partnerTenantId());
            case EITHER -> actingTenantId.equals(p.hostTenantId())
                    || actingTenantId.equals(p.partnerTenantId());
        };
        if (!ok) {
            // Enumeration-safe: an actor scoped to a tenant that is not the required
            // party cannot tell whether the partnership exists.
            throw new PartnershipNotFoundException("Partnership not found for the acting tenant");
        }
    }

    private void requirePartnershipScope(OperatorContext actor, String actingTenantId, ActionCode code) {
        try {
            tenantScopeGuard.requireTenantInScope(
                    actor, Permission.PARTNERSHIP_MANAGE, actingTenantId, code);
        } catch (TenantScopeDeniedException e) {
            // admin-api.md § Partnership Management uses the distinct code.
            throw new PartnershipScopeDeniedException(e.getMessage());
        }
    }

    private void requireHostNotPlatform(String hostTenantId) {
        if (AdminOperator.PLATFORM_TENANT_ID.equals(hostTenantId)) {
            throw new IllegalArgumentException("The platform sentinel cannot be a partnership host");
        }
    }

    private void validatePartnerTenant(String host, String partner) {
        if (partner == null || partner.isBlank()) {
            throw new IllegalArgumentException("partnerTenantId is required");
        }
        if (AdminOperator.PLATFORM_TENANT_ID.equals(partner)) {
            throw new IllegalArgumentException("partnerTenantId must not be the platform sentinel");
        }
        if (partner.equals(host)) {
            throw new IllegalArgumentException("partnerTenantId must differ from the host (self-partnership forbidden)");
        }
        if (!TENANT_ID_PATTERN.matcher(partner).matches()) {
            throw new IllegalArgumentException("partnerTenantId is malformed");
        }
    }

    private void validateDelegatedScopeCap(String host, ScopeSet delegated) {
        if (delegated.domains().isEmpty()) {
            throw new IllegalArgumentException("delegatedScope.domains must not be empty");
        }
        // ADR-045 D3 cap: no admin role may cross the org boundary.
        if (delegated.containsAdminRole()) {
            throw new PartnershipScopeInvalidException(
                    "delegatedScope must not contain an admin role (SUPER_ADMIN/TENANT_ADMIN/TENANT_BILLING_ADMIN)");
        }
        // ≤-own across org — request/invite-time double-defense. The default resolver
        // is unbounded (defers to this cap); a real host-holds resolver (deferred)
        // would reject a delegatedScope exceeding the host's holdings.
        Optional<ScopeSet> hostHolds = hostEntitledScopeResolver.resolve(host);
        if (hostHolds.isPresent() && !delegated.isSubsetOf(hostHolds.get())) {
            throw new PartnershipScopeInvalidException(
                    "delegatedScope exceeds what the host holds (≤-own across the org boundary)");
        }
    }

    private PartnershipStatus parseStatusFilter(String statusFilter) {
        if (statusFilter == null || statusFilter.isBlank()) {
            return null;
        }
        try {
            return PartnershipStatus.valueOf(statusFilter.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid status filter: " + statusFilter);
        }
    }

    private void recordAudit(ActionCode code, String partnershipId, String targetId,
                             String targetTenantId, OperatorContext actor, String reason) {
        Instant now = Instant.now();
        auditor.recordWithPermission(
                new AdminActionAuditor.AuditRecord(
                        UUID.randomUUID().toString(),
                        code,
                        actor,
                        "PARTNERSHIP",
                        targetId,
                        AuditReasons.normalize(reason),
                        null,
                        "partnership:" + code.name() + ":" + partnershipId + ":" + now.toEpochMilli(),
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

    /** Invite command (partnerTenantId + the delegated {@code {domains, roles}}). */
    public record InvitePartnershipCommand(
            String partnerTenantId,
            List<String> domains,
            List<String> roles
    ) {}
}
