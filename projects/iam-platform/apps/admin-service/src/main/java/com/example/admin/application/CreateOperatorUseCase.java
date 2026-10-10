package com.example.admin.application;

import com.example.admin.application.exception.OperatorEmailConflictException;
import com.example.admin.application.exception.OperatorInvitationRequiredException;
import com.example.admin.application.exception.TenantScopeDeniedException;
import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.application.port.OperatorLookupPort;
import com.example.admin.domain.rbac.AdminOperator;
import com.example.admin.domain.rbac.Permission;
import com.example.common.id.UuidV7;
import com.example.security.password.PasswordHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreateOperatorUseCase {

    private static final String STATUS_ACTIVE = "ACTIVE";

    private final AdminOperatorPort operatorPort;
    private final AdminActionAuditor auditor;
    private final PasswordHasher passwordHasher;
    private final OperatorLookupPort operatorLookupPort;
    private final TenantScopeGuard tenantScopeGuard;
    private final RoleGrantGuard roleGrantGuard;

    /**
     * Creates a new <b>platform-scope</b> ({@code tenantId='*'}) operator.
     *
     * <p>TASK-MONO-772 S7 (ADR-MONO-080 D6 · implementer decision D-7): a company (non-{@code '*'}) operator
     * is no longer created here — the only door is the invitation → verified self-acceptance
     * ({@code OperatorInvitationUseCase} / {@code OperatorInvitationAcceptanceUseCase}). A non-{@code '*'}
     * {@code tenantId} is refused with {@code 422 OPERATOR_INVITATION_REQUIRED} before anything is read,
     * written or called — whoever the actor is. This REPLACES the TASK-MONO-334 "a signed-up account must
     * exist in the target tenant" precondition (and its account-service search), which only ever applied
     * to non-{@code '*'} creates. {@code '*'} stays (ADR-MONO-080 D1 — platform administrators, bootstrap).
     *
     * <p>TASK-BE-249: a non-platform-scope operator cannot create a platform-scope operator
     * (defense in depth, best-effort DENIED audit — TASK-BE-262).
     */
    @Transactional
    public CreateOperatorResult createOperator(String email,
                                               String displayName,
                                               String password,
                                               List<String> roleNames,
                                               OperatorContext actor,
                                               String reason,
                                               String tenantId) {
        // TASK-MONO-772 S7: company operators come only by invitation. No audit row (nothing was
        // attempted against a tenant), no downstream call, no DB read.
        if (!AdminOperator.PLATFORM_TENANT_ID.equals(tenantId)) {
            throw new OperatorInvitationRequiredException(
                    "Company operators are created only by invitation; direct create accepts tenantId='*' only");
        }

        // --- TASK-BE-249: resolve actor's tenantId for scope guard ---
        String actorTenantId = operatorLookupPort.findByOperatorId(actor.operatorId())
                .map(OperatorLookupPort.OperatorLookupRef::tenantId)
                .orElse("fan-platform");
        boolean actorIsPlatformScope = AdminOperator.PLATFORM_TENANT_ID.equals(actorTenantId);

        // Defense-in-depth: non-platform-scope operators cannot create platform-scope operators.
        // TASK-BE-262: record a best-effort DENIED audit row before throwing.
        if (!actorIsPlatformScope) {
            auditor.recordCrossTenantDenied(
                    actor,
                    actorTenantId,
                    ActionCode.OPERATOR_CREATE,
                    "operator.create",
                    tenantId);
            throw new TenantScopeDeniedException(
                    "Only platform-scope operators may create operators with tenantId='*'");
        }

        // ADR-MONO-024 D2 (TASK-BE-345): central target-tenant confinement — the
        // actor must hold operator.manage scoped to the created operator's home
        // tenant. Net-zero: SUPER_ADMIN ('*') passes for every tenant.
        tenantScopeGuard.requireTenantInScope(
                actor, Permission.OPERATOR_MANAGE, tenantId, ActionCode.OPERATOR_CREATE);

        // TASK-BE-262: use per-tenant check matching the (tenant_id, email) composite unique index
        // introduced by V0025. Same email in a different tenant is a separate, valid operator.
        String normalizedEmail = email == null ? null : email.trim().toLowerCase();
        if (normalizedEmail != null && operatorPort.existsByTenantIdAndEmail(tenantId, normalizedEmail)) {
            throw new OperatorEmailConflictException("Operator email already exists");
        }

        Map<String, AdminOperatorPort.RoleView> resolvedRoles = operatorPort.resolveRolesByName(roleNames);

        // ADR-MONO-024 D3 (TASK-BE-347): grant-menu no-escalation on the created
        // operator's roles — the actor may grant only roles ⊆ its own permissions
        // (never SUPER_ADMIN). Net-zero for SUPER_ADMIN ('*').
        roleGrantGuard.requireGrantable(actor, resolvedRoles.values(), ActionCode.OPERATOR_CREATE);

        Instant now = Instant.now();
        String operatorUuid = UuidV7.randomString();
        // TASK-BE-377 / ADR-MONO-035 O2 (4c): the password is OPTIONAL. When omitted,
        // the operator is created OIDC-only (password_hash = NULL) — their primary login
        // is the unified IAM OIDC credential via the ADR-014 token-exchange. When
        // supplied, it is hashed and retained as a break-glass local login (the policy
        // is enforced at the DTO via @Size/@Pattern when present).
        String passwordHash = (password == null || password.isBlank())
                ? null
                : passwordHasher.hash(password);

        AdminOperatorPort.OperatorView created = operatorPort.createOperator(
                new AdminOperatorPort.NewOperator(
                        operatorUuid, tenantId, normalizedEmail, passwordHash,
                        displayName, STATUS_ACTIVE, now));

        Long actorInternalId = operatorPort.resolveActorInternalId(
                actor == null ? null : actor.operatorId());
        List<AdminOperatorPort.NewRoleBinding> bindings = new ArrayList<>(resolvedRoles.size());
        for (AdminOperatorPort.RoleView role : resolvedRoles.values()) {
            bindings.add(new AdminOperatorPort.NewRoleBinding(
                    created.internalId(), role.id(), now, actorInternalId, tenantId));
        }
        operatorPort.saveOperatorRoles(bindings);

        // ADR-MONO-034 U4 (step 3d) identity resolve-or-link is NOT done here: it was always skipped for
        // '*' (no account_db tenant row to anchor the identity), and '*' is now the only tenant this
        // use case creates (TASK-MONO-772 S7). Platform operators stay unlinked (link manually via 3c).

        String auditId = auditor.newAuditId();
        auditor.record(new AdminActionAuditor.AuditRecord(
                auditId,
                ActionCode.OPERATOR_CREATE,
                actor,
                "OPERATOR",
                operatorUuid,
                AuditReasons.normalize(reason),
                null,
                "create:" + auditId,
                Outcome.SUCCESS,
                null,
                now,
                Instant.now(),
                tenantId));  // TASK-BE-249: targetTenantId = created operator's tenant

        List<String> roleNamesOut = new ArrayList<>(resolvedRoles.keySet());
        return new CreateOperatorResult(
                operatorUuid,
                created.email(),
                created.displayName(),
                created.status(),
                roleNamesOut,
                false,
                created.createdAt(),
                auditId,
                tenantId);
    }

    public record CreateOperatorResult(
            String operatorId,
            String email,
            String displayName,
            String status,
            List<String> roles,
            boolean totpEnrolled,
            Instant createdAt,
            String auditId,
            String tenantId
    ) {
        /** Backward-compat 8-arg constructor for test fixtures predating TASK-BE-249. */
        public CreateOperatorResult(String operatorId, String email, String displayName,
                                    String status, List<String> roles, boolean totpEnrolled,
                                    Instant createdAt, String auditId) {
            this(operatorId, email, displayName, status, roles, totpEnrolled,
                    createdAt, auditId, "fan-platform");
        }
    }
}
