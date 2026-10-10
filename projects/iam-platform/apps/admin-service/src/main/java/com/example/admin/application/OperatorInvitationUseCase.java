package com.example.admin.application;

import com.example.admin.application.exception.IdempotencyKeyConflictException;
import com.example.admin.application.exception.OperatorInvitationNotFoundException;
import com.example.admin.application.exception.OperatorInvitationNotPendingException;
import com.example.admin.application.exception.OperatorNotFoundException;
import com.example.admin.application.exception.TenantScopeDeniedException;
import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.application.port.OperatorInvitationMailPort;
import com.example.admin.application.port.OperatorInvitationMailPort.DeliveryStatus;
import com.example.admin.application.port.OperatorInvitationPort;
import com.example.admin.application.port.OperatorInvitationPort.InvitationView;
import com.example.admin.application.port.TenantProvisioningPort;
import com.example.admin.domain.rbac.AdminOperator;
import com.example.admin.domain.rbac.Permission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * TASK-MONO-772 S2 (ADR-MONO-080 D6 · rider R4 · owner decisions OD-1 · OD-4) — the management surface of the
 * operator invitation: create · list · cancel · resend (admin-api.md § Operator Invitation). The acceptance is
 * S3's (auth → admin internal) and is not here.
 *
 * <p><b>Who.</b> {@code operator.manage} is the endpoint gate ({@code @RequiresPermission}). On top of it:
 * ADR-MONO-024 D2 — the target tenant must be in the actor's admin-grant scope ({@link TenantScopeGuard}, the one
 * decision site); D3 — every invited role must be in the actor's grant menu ({@link RoleGrantGuard}). A
 * {@code TENANT_ADMIN} can therefore invite only into its own tenant and only roles it could grant directly;
 * {@code SUPER_ADMIN} ({@code '*'}) invites anywhere, including a tenant with no operator yet (773's «first admin»).
 *
 * <p><b>R4.</b> The token is 32 random bytes; only its SHA-256 hex is stored. The raw value lives in this class
 * for one call — from {@link OperatorInvitationWriter} to {@link OperatorInvitationMailPort} — and is never
 * returned, logged or audited. Expiry is judged when read.
 *
 * <p><b>Mail after commit, no retry.</b> The row (and its audit row) commit first; then the mail is sent once and
 * its result written to the row's {@code delivery} in a short transaction of its own. A failed mail is a
 * {@code 201}/{@code 200} with {@code delivery.status = FAILED_*}, not an error — the resend is the retry.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperatorInvitationUseCase {

    /** account-service's tenant-id shape ({@code TenantId.PATTERN}); {@code '*'} never matches. */
    static final Pattern TENANT_ID_PATTERN = Pattern.compile("^[a-z][a-z0-9-]{1,31}$");
    /** Deliberately loose (the real proof of the address is the verified-email acceptance, not this regex). */
    static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    static final Set<String> STATUSES = Set.of("PENDING", "ACCEPTED", "CANCELLED");
    static final int MAX_IDEMPOTENCY_KEY = 100;

    private final OperatorInvitationWriter writer;
    private final OperatorInvitationPort invitationPort;
    private final OperatorInvitationMailPort mailPort;
    private final AdminOperatorPort operatorPort;
    private final TenantProvisioningPort provisioningPort;
    private final TenantScopeGuard tenantScopeGuard;
    private final TenantScopeResolver tenantScopeResolver;
    private final RoleGrantGuard roleGrantGuard;
    private final AdminActionAuditor auditor;

    // ── create ──────────────────────────────────────────────────────────────

    public OperatorInvitationResult create(CreateOperatorInvitationCommand cmd) {
        // 2. reason (the controller already refused a missing header) → Idempotency-Key → body
        AuditReasons.require(cmd.reason());
        String key = cmd.idempotencyKey();
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key header is required");
        }
        if (key.length() > MAX_IDEMPOTENCY_KEY) {
            throw new IllegalArgumentException("Idempotency-Key must be at most " + MAX_IDEMPOTENCY_KEY + " characters");
        }
        String email = normalizeEmail(cmd.email());
        String displayName = requireDisplayName(cmd.displayName());
        String tenantId = requireInvitableTenant(cmd.tenantId());
        if (cmd.roles() == null) {
            throw new IllegalArgumentException("roles is required (an array of role names)");
        }

        // 3. D2 — the invitation's tenant must be administrable by the actor (DENIED row on refusal).
        tenantScopeGuard.requireTenantInScope(
                cmd.actor(), Permission.OPERATOR_MANAGE, tenantId, ActionCode.OPERATOR_INVITATION_CREATE);

        // 4. unknown role → 400 ROLE_NOT_FOUND, then D3 no-escalation → 403 ROLE_GRANT_FORBIDDEN (DENIED row).
        Map<String, AdminOperatorPort.RoleView> roles = operatorPort.resolveRolesByName(cmd.roles());
        roleGrantGuard.requireGrantable(cmd.actor(), roles.values(), ActionCode.OPERATOR_INVITATION_CREATE);

        // 5. a re-sent key → 409, no row (its audit INSERT would otherwise die on idx_admin_actions_idemp → 500).
        if (auditor.isIdempotencyKeyUsed(cmd.actor().operatorId(), ActionCode.OPERATOR_INVITATION_CREATE, key)) {
            throw new IdempotencyKeyConflictException(
                    "Idempotency-Key already used for OPERATOR_INVITATION_CREATE by this operator; retry with a new key");
        }

        // 6. the tenant exists at the authority — 404 / 503 and NOTHING written (no invitation to a ghost tenant).
        provisioningPort.get(tenantId);

        // 7–9. conflicts · row · audit row — one transaction, committed when this returns.
        OperatorInvitationWriter.Issued issued = writer.createPending(cmd.actor(), tenantId, email, displayName,
                new ArrayList<>(roles.keySet()), key, cmd.reason());

        // 10. after commit: the mail, once; its result onto the row.
        InvitationView after = deliver(issued, inviterName(cmd.actor()));
        return toResult(after, Instant.now()).withAuditId(issued.auditId());
    }

    // ── list ────────────────────────────────────────────────────────────────

    /**
     * Read — no audit row. The tenant rule is {@code GET /api/admin/operators}'s, verbatim: omitted → the
     * caller's home tenant; within the caller's effective scope (home ∪ assignments) → that tenant; {@code '*'} →
     * every tenant, platform-scope callers only; anything else → 403 {@code TENANT_SCOPE_DENIED}.
     */
    public OperatorInvitationPageResult list(OperatorContext actor, String requestedTenantId, String status,
                                             int page, int size) {
        String effectiveStatus = status == null || status.isBlank() ? "PENDING" : status.trim().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(effectiveStatus)) {
            throw new IllegalArgumentException("status must be one of " + STATUSES);
        }
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("size must be between 1 and 100");
        }
        AdminOperatorPort.OperatorView caller = operatorPort.findByOperatorId(actor.operatorId())
                .orElseThrow(() -> new TenantScopeDeniedException("Operator not found: " + actor.operatorId()));
        String callerTenantId = caller.tenantId();
        boolean platform = AdminOperator.PLATFORM_TENANT_ID.equals(callerTenantId);
        String tenantId = requestedTenantId == null || requestedTenantId.isBlank() ? callerTenantId : requestedTenantId;

        if (!platform) {
            Set<String> effective = tenantScopeResolver.resolveEffectiveTenantScope(caller.internalId(), callerTenantId);
            if (!effective.contains(tenantId)) {
                throw new TenantScopeDeniedException("Operator tenantId=" + callerTenantId
                        + " cannot list operator invitations of tenantId=" + tenantId);
            }
        }
        boolean allTenants = platform && AdminOperator.PLATFORM_TENANT_ID.equals(tenantId);
        OperatorInvitationPort.InvitationPage rows =
                invitationPort.findPage(allTenants ? null : tenantId, effectiveStatus, page, size);
        Instant now = Instant.now();
        List<OperatorInvitationResult> content = new ArrayList<>(rows.content().size());
        for (InvitationView v : rows.content()) {
            content.add(toResult(v, now));
        }
        return new OperatorInvitationPageResult(content, rows.totalElements(), rows.page(), rows.size(),
                rows.totalPages());
    }

    // ── cancel ──────────────────────────────────────────────────────────────

    /** PENDING (expired included) → CANCELLED; CANCELLED → 200 no-op without an audit row; ACCEPTED → 409. */
    public OperatorInvitationResult cancel(OperatorContext actor, String invitationId, String reason) {
        AuditReasons.require(reason);
        InvitationView invitation = findInScope(actor, invitationId);
        switch (invitation.status()) {
            case "CANCELLED" -> {
                return toResult(invitation, Instant.now());
            }
            case "ACCEPTED" -> throw new OperatorInvitationNotPendingException(
                    "An accepted invitation is not cancelled — change the operator's status, roles or assignments");
            default -> writer.cancel(actor, invitation, reason);
        }
        return toResult(reload(invitation), Instant.now());
    }

    // ── resend ──────────────────────────────────────────────────────────────

    /**
     * A new token on the same row (the old link dies), a new expiry, the re-sender as {@code invitedBy}. The
     * re-sender must be able to grant the invited roles (D3, judged for THIS actor) and the tenant must still be
     * registered — both checked before the token rotates.
     */
    public OperatorInvitationResult resend(OperatorContext actor, String invitationId, String reason) {
        AuditReasons.require(reason);
        InvitationView invitation = findInScope(actor, invitationId);
        if (!"PENDING".equals(invitation.status())) {
            throw new OperatorInvitationNotPendingException(
                    "Only a pending invitation can be resent (it is " + invitation.status() + ")");
        }
        Map<String, AdminOperatorPort.RoleView> roles = operatorPort.resolveRolesByName(invitation.roles());
        roleGrantGuard.requireGrantable(actor, roles.values(), ActionCode.OPERATOR_INVITATION_RESEND);
        provisioningPort.get(invitation.tenantId());

        OperatorInvitationWriter.Issued issued = writer.rotate(actor, invitation, reason);
        return toResult(deliver(issued, inviterName(actor)), Instant.now());
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    /**
     * The invitation, if it exists AND its tenant is in the actor's admin-grant scope; otherwise one 404 for both
     * (a 403 would confirm that an invitation exists in a tenant the actor cannot see).
     */
    private InvitationView findInScope(OperatorContext actor, String invitationId) {
        InvitationView invitation = invitationPort.findByInvitationId(invitationId)
                .orElseThrow(() -> new OperatorInvitationNotFoundException("Operator invitation not found"));
        if (!tenantScopeGuard.isTenantInScope(actor, Permission.OPERATOR_MANAGE, invitation.tenantId())) {
            throw new OperatorInvitationNotFoundException("Operator invitation not found");
        }
        return invitation;
    }

    private InvitationView reload(InvitationView invitation) {
        return invitationPort.findByInvitationId(invitation.invitationId()).orElse(invitation);
    }

    /** Sends the mail for the token just committed and records the result against that token's hash. */
    private InvitationView deliver(OperatorInvitationWriter.Issued issued, String inviterDisplayName) {
        InvitationView inv = issued.invitation();
        DeliveryStatus status;
        try {
            status = mailPort.send(new OperatorInvitationMailPort.InvitationMail(
                    inv.email(), issued.token(), inv.tenantId(), inviterDisplayName, inv.expiresAt()));
        } catch (RuntimeException e) {
            // A port that broke its «never throws» contract still cannot turn a committed invitation into an error.
            log.warn("operator-invitation mail port threw {}; recorded as FAILED_TRANSIENT",
                    e.getClass().getSimpleName());
            status = DeliveryStatus.FAILED_TRANSIENT;
        }
        try {
            invitationPort.recordDelivery(inv.internalId(), inv.tokenHash(), status.name(), Instant.now());
        } catch (RuntimeException e) {
            // The invitation and its mail stand; only the «delivery» column could not be written.
            log.warn("operator-invitation delivery result not recorded (invitation={}): {}",
                    inv.invitationId(), e.getClass().getSimpleName());
        }
        log.info("operator-invitation mail {} (invitation={}, tenant={})", status, inv.invitationId(), inv.tenantId());
        return reload(inv);
    }

    private String inviterName(OperatorContext actor) {
        return operatorPort.findByOperatorId(actor.operatorId())
                .map(AdminOperatorPort.OperatorView::displayName)
                .orElseThrow(() -> new OperatorNotFoundException("Operator not found for operatorId=" + actor.operatorId()));
    }

    static OperatorInvitationResult toResult(InvitationView v, Instant now) {
        return new OperatorInvitationResult(
                v.invitationId(), v.tenantId(), v.email(), v.displayName(), v.roles(), v.status(),
                OperatorInvitationResult.isExpired(v.status(), v.expiresAt(), now),
                v.expiresAt(), v.createdAt(), v.invitedByOperatorId(),
                v.lastDeliveryStatus(), v.lastDeliveryAt(),
                v.acceptedAt(), v.acceptedOperatorId(), v.cancelledAt(), null);
    }

    static String normalizeEmail(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("email is required");
        }
        String email = raw.trim().toLowerCase(Locale.ROOT);
        if (email.length() > 255 || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("email must be a valid address of at most 255 characters");
        }
        return email;
    }

    static String requireDisplayName(String raw) {
        if (raw == null || raw.isBlank() || raw.trim().length() > 64) {
            throw new IllegalArgumentException("displayName is required (1–64 characters)");
        }
        return raw.trim();
    }

    /** {@code '*'} → 400: the platform scope is never invited (ADR-MONO-080 D1). */
    static String requireInvitableTenant(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("tenantId is required");
        }
        if (AdminOperator.PLATFORM_TENANT_ID.equals(raw)) {
            throw new IllegalArgumentException(
                    "tenantId '*' cannot be invited — platform operators are not created by invitation");
        }
        if (!TENANT_ID_PATTERN.matcher(raw).matches()) {
            throw new IllegalArgumentException("tenantId must match " + TENANT_ID_PATTERN.pattern());
        }
        return raw;
    }
}
