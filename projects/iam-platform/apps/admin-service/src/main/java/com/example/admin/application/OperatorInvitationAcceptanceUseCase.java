package com.example.admin.application;

import com.example.admin.application.exception.OperatorAlreadyProvisionedException;
import com.example.admin.application.exception.OperatorEmailConflictException;
import com.example.admin.application.exception.OperatorInvitationAccountNotEligibleException;
import com.example.admin.application.exception.OperatorInvitationAlreadyUsedException;
import com.example.admin.application.exception.OperatorInvitationEmailMismatchException;
import com.example.admin.application.exception.OperatorInvitationEmailNotVerifiedException;
import com.example.admin.application.exception.OperatorInvitationException;
import com.example.admin.application.exception.OperatorInvitationExpiredException;
import com.example.admin.application.exception.OperatorInvitationInvalidatedException;
import com.example.admin.application.exception.OperatorInvitationNotFoundException;
import com.example.admin.application.exception.RoleNotFoundException;
import com.example.admin.application.exception.TenantNotFoundException;
import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.application.port.OperatorIdentityResolvePort;
import com.example.admin.application.port.OperatorInvitationPort;
import com.example.admin.application.port.OperatorInvitationPort.InvitationView;
import com.example.admin.application.port.TenantProvisioningPort;
import com.example.admin.application.port.VerifiedEmailMatchPort;
import com.example.admin.application.tenant.TenantSummary;
import com.example.admin.domain.rbac.Permission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * TASK-MONO-772 S3 (ADR-MONO-080 D6 · rider R4 · implementer decision D-3 · owner decision OD-1) — the two reads
 * and the one write behind the IdP acceptance page (auth-to-admin.md § preview · § accept).
 *
 * <p><b>Fail-closed order</b> — the first refusal answers, and 🔴 <b>a refusal writes nothing</b> (the invitation
 * stays PENDING so the right person can still accept it):
 * <ol>
 *   <li>the token's SHA-256 finds the invitation — none · CANCELLED · a token a resend replaced → 404 (one answer,
 *       enumeration-safe);</li>
 *   <li>ACCEPTED — by this account → 200 with the first result; by another → 409 {@code ALREADY_USED} (before the
 *       expiry check, S1-2);</li>
 *   <li>expired → 410;</li>
 *   <li>🔴 account-service's verdict ({@link VerifiedEmailMatchPort}): an ACTIVE pool account → the invited email →
 *       verified (TASK-MONO-770's {@code VerifiedEmailRequirement}, called where it lives). No verdict → the
 *       downstream exception propagates (503) — never «attach without knowing»;</li>
 *   <li>the basis still holds — tenant ACTIVE, inviter ACTIVE, tenant still in the inviter's admin-grant scope (D2),
 *       every role still in the inviter's grant menu (D3) → otherwise 409 {@code INVALIDATED};</li>
 *   <li>OD-1 — no operator row has {@code oidc_subject = accountId} (any status) → otherwise 409;</li>
 *   <li>no operator with this email in the tenant → otherwise 409 {@code OPERATOR_EMAIL_CONFLICT};</li>
 *   <li>the transaction ({@link OperatorInvitationAcceptanceWriter}).</li>
 * </ol>
 *
 * <p>Refusals are not audited in {@code admin_actions} (its actor must be an operator, and a refused acceptor has
 * none — S1-12); each is one structured log line: invitation id · code · account id. 🔴 Never the token, its hash
 * or the email (R4).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperatorInvitationAcceptanceUseCase {

    static final String STATUS_PENDING = "PENDING";
    static final String STATUS_ACCEPTED = "ACCEPTED";
    static final String STATUS_CANCELLED = "CANCELLED";
    static final String ACTIVE = "ACTIVE";

    private final OperatorInvitationPort invitationPort;
    private final OperatorInvitationAcceptanceWriter writer;
    private final VerifiedEmailMatchPort verifiedEmailMatchPort;
    private final TenantProvisioningPort provisioningPort;
    private final AdminOperatorPort operatorPort;
    private final OperatorOidcSubjectResolver oidcSubjectResolver;
    private final TenantScopeGuard tenantScopeGuard;
    private final RoleGrantGuard roleGrantGuard;
    private final OperatorIdentityResolvePort identityResolvePort;

    // ── preview ─────────────────────────────────────────────────────────────

    /** Read-only: no row, no audit, no event. ACCEPTED and expired are answers (200), not errors. */
    public OperatorInvitationPreviewResult preview(String token) {
        String rawToken = requireText(token, "token");
        InvitationView invitation = findLive(OperatorInvitationTokens.sha256Hex(rawToken))
                .orElseThrow(() -> new OperatorInvitationNotFoundException("Operator invitation not found"));
        Instant now = Instant.now();
        return new OperatorInvitationPreviewResult(
                invitation.tenantId(),
                tenantDisplayName(invitation.tenantId()),
                maskEmail(invitation.email()),
                invitation.roles(),
                invitation.status(),
                OperatorInvitationResult.isExpired(invitation.status(), invitation.expiresAt(), now),
                invitation.expiresAt());
    }

    // ── accept ──────────────────────────────────────────────────────────────

    public OperatorInvitationAcceptResult accept(String token, String accountId) {
        String rawToken = requireText(token, "token");
        String account = requireText(accountId, "accountId").trim();
        String tokenHash = OperatorInvitationTokens.sha256Hex(rawToken);
        String invitationId = null;
        try {
            // 1. the invitation (a CANCELLED one is «not found»).
            InvitationView invitation = findLive(tokenHash)
                    .orElseThrow(() -> new OperatorInvitationNotFoundException("Operator invitation not found"));
            invitationId = invitation.invitationId();

            // 2. already accepted — the same account gets its first result back; anyone else is refused.
            if (STATUS_ACCEPTED.equals(invitation.status())) {
                return alreadyAccepted(invitation, account);
            }

            // 3. expired (judged on read — EXPIRED is not a stored value).
            Instant now = Instant.now();
            if (!invitation.expiresAt().isAfter(now)) {
                throw new OperatorInvitationExpiredException("The operator invitation has expired");
            }

            // 4. 🔴 the verified-owner predicate — account-service's verdict, fail-closed.
            requireVerifiedOwner(account, invitation.email());

            // 5. the basis the invitation was issued on.
            Map<String, AdminOperatorPort.RoleView> roles = requireBasis(invitation);

            // 6. OD-1 — one person, one company (any status: a suspended facet is still a facet).
            if (oidcSubjectResolver.resolve(account).isPresent()) {
                throw new OperatorAlreadyProvisionedException("This account already has an operator facet");
            }

            // 7. an operator of this tenant with this email appeared since the invitation was issued.
            if (operatorPort.existsByTenantIdAndEmail(invitation.tenantId(), invitation.email())) {
                throw new OperatorEmailConflictException("An operator with this email already exists in the tenant");
            }

            // 8. the transaction (a lost race is answered by the rules above, see write()).
            return write(invitation, account, roles);
        } catch (OperatorInvitationException | OperatorEmailConflictException e) {
            String code = e instanceof OperatorInvitationException oie ? oie.getCode() : "OPERATOR_EMAIL_CONFLICT";
            log.info("operator-invitation accept refused (invitation={}, code={}, account={})",
                    invitationId == null ? "-" : invitationId, code, account);
            throw e;
        }
    }

    // ── steps ───────────────────────────────────────────────────────────────

    /** The invitation the token currently belongs to, unless it was cancelled. */
    private Optional<InvitationView> findLive(String tokenHash) {
        return invitationPort.findByTokenHash(tokenHash)
                .filter(inv -> !STATUS_CANCELLED.equals(inv.status()));
    }

    private OperatorInvitationAcceptResult alreadyAccepted(InvitationView invitation, String accountId) {
        if (accountId.equals(invitation.acceptedAccountId())) {
            return new OperatorInvitationAcceptResult(
                    invitation.acceptedOperatorId(), invitation.tenantId(), invitation.roles(), true);
        }
        throw new OperatorInvitationAlreadyUsedException("The operator invitation was already accepted by another account");
    }

    /**
     * Step 4. The order inside is account-service's (pool account → email → verified); the three refusals map
     * one-to-one. A failure to get a verdict is NOT caught here — it propagates as the downstream exception it is
     * (503 {@code DOWNSTREAM_ERROR} / {@code CIRCUIT_OPEN}).
     */
    private void requireVerifiedOwner(String accountId, String invitedEmail) {
        VerifiedEmailMatchPort.MatchResult verdict = verifiedEmailMatchPort.match(accountId, invitedEmail);
        if (verdict == null || verdict.outcome() == null) {
            throw new IllegalStateException("verified-email match returned no verdict");
        }
        switch (verdict.outcome()) {
            case MATCHED -> {
                if (verdict.emailVerifiedAt() == null) {
                    // MATCHED without its evidence is not a match (the adapter already refuses this; belt and braces).
                    throw new OperatorInvitationEmailNotVerifiedException("The account's email is not verified");
                }
            }
            case NOT_ELIGIBLE -> throw new OperatorInvitationAccountNotEligibleException(
                    "Only an active consumer-pool account can accept an operator invitation");
            case EMAIL_MISMATCH -> throw new OperatorInvitationEmailMismatchException(
                    "The signed-in account's email is not the invited address");
            case NOT_VERIFIED -> throw new OperatorInvitationEmailNotVerifiedException(
                    "The account's email is not verified");
        }
    }

    /**
     * Step 5 (S1-4 — one code for all four): the tenant is ACTIVE, the operator whose live token this is is ACTIVE,
     * the tenant is still in that operator's admin-grant scope (D2) and every invited role is still in that
     * operator's grant menu (D3) — the same two decision sites the issuance used, asked again. A tenant read
     * failure propagates (503); a tenant that no longer exists is «not ACTIVE».
     */
    private Map<String, AdminOperatorPort.RoleView> requireBasis(InvitationView invitation) {
        String tenantId = invitation.tenantId();
        TenantSummary tenant;
        try {
            tenant = provisioningPort.get(tenantId);
        } catch (TenantNotFoundException e) {
            throw invalidated("tenant no longer exists");
        }
        if (tenant == null || !ACTIVE.equals(tenant.status())) {
            throw invalidated("tenant is not ACTIVE");
        }

        String inviterId = invitation.invitedByOperatorId();
        boolean inviterActive = inviterId != null && operatorPort.findByOperatorId(inviterId)
                .map(op -> ACTIVE.equals(op.status()))
                .orElse(false);
        if (!inviterActive) {
            throw invalidated("inviter is not ACTIVE");
        }
        OperatorContext inviter = new OperatorContext(inviterId, null);
        if (!tenantScopeGuard.isTenantInScope(inviter, Permission.OPERATOR_MANAGE, tenantId)) {
            throw invalidated("tenant left the inviter's admin-grant scope");
        }

        Map<String, AdminOperatorPort.RoleView> roles;
        try {
            roles = operatorPort.resolveRolesByName(invitation.roles());
        } catch (RoleNotFoundException e) {
            throw invalidated("an invited role no longer exists");
        }
        if (roles.isEmpty()) {
            throw invalidated("the invitation carries no role");
        }
        List<String> grantable = roleGrantGuard.grantableRoleNames(inviter, roles.values());
        if (!grantable.containsAll(roles.keySet())) {
            throw invalidated("an invited role left the inviter's grant menu");
        }
        return roles;
    }

    private static OperatorInvitationInvalidatedException invalidated(String why) {
        return new OperatorInvitationInvalidatedException("The operator invitation is no longer valid: " + why);
    }

    /**
     * Step 8, and what a lost race means. A lost claim (0 rows) or a UNIQUE violation is re-read OUTSIDE the
     * rolled-back transaction and answered by the same rules: accepted by this account (a double submit) → its
     * first result; by another → 409; cancelled / re-sent → 404; still pending but past expiry → 410;
     * {@code oidc_subject} taken → OD-1 409; {@code (tenant_id, email)} taken → 409.
     */
    private OperatorInvitationAcceptResult write(InvitationView invitation, String accountId,
                                                 Map<String, AdminOperatorPort.RoleView> roles) {
        OperatorInvitationAcceptanceWriter.Accepted accepted;
        try {
            accepted = writer.accept(invitation, accountId, roles.values(), invitation.invitedByInternalId());
        } catch (OperatorInvitationAcceptanceWriter.ClaimLost lost) {
            InvitationView now = invitationPort.findByInvitationId(invitation.invitationId()).orElse(null);
            if (now == null || STATUS_CANCELLED.equals(now.status())
                    || !invitation.tokenHash().equals(now.tokenHash())) {
                throw new OperatorInvitationNotFoundException("Operator invitation not found");
            }
            if (STATUS_ACCEPTED.equals(now.status())) {
                return alreadyAccepted(now, accountId);
            }
            throw new OperatorInvitationExpiredException("The operator invitation has expired");
        } catch (DataIntegrityViolationException e) {
            if (oidcSubjectResolver.resolve(accountId).isPresent()) {
                throw new OperatorAlreadyProvisionedException("This account already has an operator facet");
            }
            if (operatorPort.existsByTenantIdAndEmail(invitation.tenantId(), invitation.email())) {
                throw new OperatorEmailConflictException("An operator with this email already exists in the tenant");
            }
            throw e;
        }
        log.info("operator-invitation accepted (invitation={}, operator={}, tenant={}, account={})",
                invitation.invitationId(), accepted.operatorId(), invitation.tenantId(), accountId);
        linkIdentityFailSoft(accepted.operatorInternalId(), invitation.tenantId(), invitation.email());
        return new OperatorInvitationAcceptResult(accepted.operatorId(), invitation.tenantId(), invitation.roles(), false);
    }

    private void linkIdentityFailSoft(long operatorInternalId, String tenantId, String email) {
        try {
            String identityId = identityResolvePort.resolveOrCreateIdentity(tenantId, email);
            if (identityId != null) {
                operatorPort.linkIdentity(operatorInternalId, identityId, Instant.now());
            }
        } catch (RuntimeException e) {
            // The acceptance has committed; an unlinked operator is reconciled later (identity:link).
            log.warn("operator-invitation accepted but identity link failed (fail-soft, operator left unlinked): {}",
                    e.getClass().getSimpleName());
        }
    }

    private String tenantDisplayName(String tenantId) {
        try {
            TenantSummary tenant = provisioningPort.get(tenantId);
            return tenant == null ? null : tenant.displayName();
        } catch (RuntimeException e) {
            // Fail-soft: the preview is a picture, not a verdict — the page falls back to the tenant id.
            log.info("operator-invitation preview: tenant display name unavailable (tenant={}): {}",
                    tenantId, e.getClass().getSimpleName());
            return null;
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    /**
     * {@code p*****@example.com} (auth-to-admin.md § preview example): the first character, a fixed run of stars
     * (the local part's length is not revealed), the domain.
     */
    static String maskEmail(String email) {
        if (email == null) {
            return null;
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "*****";
        }
        return email.charAt(0) + "*****" + email.substring(at);
    }
}
