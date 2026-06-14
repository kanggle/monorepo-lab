package com.example.admin.application;

import com.example.admin.application.exception.OperatorEmailConflictException;
import com.example.admin.application.exception.TenantScopeDeniedException;
import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.application.port.OperatorLookupPort;
import com.example.admin.domain.rbac.AdminOperator;
import com.example.admin.domain.rbac.Permission;
import com.example.admin.infrastructure.client.AccountServiceClient;
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
    private final AccountServiceClient accountServiceClient;

    /**
     * Creates a new operator.
     *
     * <p>TASK-BE-249: {@code tenantId} is now required. Defense-in-depth rule:
     * a non-platform-scope operator cannot create a platform-scope ({@code "*"})
     * operator (spec §Implementation Notes).
     */
    @Transactional
    public CreateOperatorResult createOperator(String email,
                                               String displayName,
                                               String password,
                                               List<String> roleNames,
                                               OperatorContext actor,
                                               String reason,
                                               String tenantId,
                                               Boolean reuseExistingIdentity) {
        // --- TASK-BE-249: resolve actor's tenantId for scope guard ---
        String actorTenantId = operatorLookupPort.findByOperatorId(actor.operatorId())
                .map(OperatorLookupPort.OperatorSummary::tenantId)
                .orElse("fan-platform");
        boolean actorIsPlatformScope = AdminOperator.PLATFORM_TENANT_ID.equals(actorTenantId);

        // Defense-in-depth: non-platform-scope operators cannot create platform-scope operators.
        // TASK-BE-262: record a best-effort DENIED audit row before throwing.
        if (AdminOperator.PLATFORM_TENANT_ID.equals(tenantId) && !actorIsPlatformScope) {
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

        // ADR-MONO-034 U4 (step 3d): attach the new operator to a central identity so
        // post-step-3 operators stop the identity divergence. Fail-soft (the OPPOSITE
        // of the 3c link's fail-closed): the operator-create must NOT hard-fail on
        // identity-infra unavailability — the operator can be linked later via the
        // explicit 3c surface.
        //
        // Platform-scope ('*') operators are SKIPPED: there is no account_db tenant
        // row for '*', so the central identities FK (within account_db) would have no
        // tenant to anchor — platform operators stay unlinked (link manually via 3c if
        // ever needed).
        if (!AdminOperator.PLATFORM_TENANT_ID.equals(tenantId)) {
            String identityId = accountServiceClient.resolveOrCreateIdentity(
                    tenantId, normalizedEmail,
                    reuseExistingIdentity != null && reuseExistingIdentity);  // fail-soft → may be null
            if (identityId != null) {
                operatorPort.linkIdentity(created.internalId(), identityId, now);
            }
        }

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

    /**
     * Backward-compat overload for call sites predating TASK-BE-374
     * ({@code reuseExistingIdentity} added). Defaults the identity-reuse opt-in to
     * {@code false} (no silent merge — ADR-034 U3).
     *
     * @deprecated Supply {@code reuseExistingIdentity} explicitly (TASK-BE-374).
     */
    @Deprecated
    @Transactional
    public CreateOperatorResult createOperator(String email,
                                               String displayName,
                                               String password,
                                               List<String> roleNames,
                                               OperatorContext actor,
                                               String reason,
                                               String tenantId) {
        return createOperator(email, displayName, password, roleNames, actor, reason, tenantId, false);
    }

    /**
     * Backward-compat overload for call sites predating TASK-BE-249.
     * Defaults {@code tenantId} to {@code "fan-platform"}.
     *
     * @deprecated Supply {@code tenantId} explicitly.
     */
    @Deprecated
    @Transactional
    public CreateOperatorResult createOperator(String email,
                                               String displayName,
                                               String password,
                                               List<String> roleNames,
                                               OperatorContext actor,
                                               String reason) {
        return createOperator(email, displayName, password, roleNames, actor, reason, "fan-platform", false);
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
