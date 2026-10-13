package com.example.admin.application;

import com.example.admin.application.exception.OperatorNotFoundException;
import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.domain.rbac.AdminOperator;
import com.example.admin.domain.rbac.Permission;
import com.example.admin.infrastructure.persistence.rbac.CachingPermissionEvaluator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * {@code PATCH /api/admin/operators/{operatorId}/roles}.
 *
 * <p><b>TASK-MONO-784 S4 (ADR-MONO-082 b · D3 · decision D-7) — a role edit is on the COMPANY axis.</b> It
 * replaces only the target's DIRECT role rows of the acting company T ({@code tenant_id = T ∧ group_origin IS
 * NULL ∧ org_node_id IS NULL}). Before 784 it deleted every role row of the operator and rewrote them in the home
 * tenant: an edit in company A wiped the person's roles in company B (784 Failure Scenario 3), and even a
 * single-company edit silently converted group fan-out rows and org-node grants into direct rows (784 AC-0 F6).
 *
 * <p>Acting tenant T (admin-api.md § PATCH …/roles, S1-1): a {@code '*'} target → T = {@code '*'} whatever the
 * header says (a platform operator's roles are {@code '*'} rows only); otherwise {@code X-Tenant-Id}; header
 * omitted → the target's single membership (two or more → 400, none → 404). Then, in order: T ∈ the actor's
 * {@code operator.manage} grant scope (403 + DENIED row) → a non-{@code '*'} target belongs to T (404 — enumeration
 * safe) and never gets a {@code '*'} row (400, ADR-082 D2) → ≤-own in T (D-6) → T keeps an administrator (409) →
 * replace.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PatchOperatorRoleUseCase {

    private final AdminOperatorPort operatorPort;
    private final AdminActionAuditor auditor;
    private final CachingPermissionEvaluator cachingPermissionEvaluator;
    private final TenantScopeGuard tenantScopeGuard;
    private final RoleGrantGuard roleGrantGuard;
    private final TenantScopeResolver tenantScopeResolver;
    private final TenantLastAdminGuard lastAdminGuard;

    @Transactional
    public PatchRolesResult patchRoles(String operatorUuid,
                                       List<String> roleNames,
                                       String actingTenantHeader,
                                       OperatorContext actor,
                                       String reason) {
        AdminOperatorPort.OperatorView operator = operatorPort.findByOperatorId(operatorUuid)
                .orElseThrow(() -> new OperatorNotFoundException(
                        "Operator not found for operatorId=" + operatorUuid));

        boolean targetIsPlatform = AdminOperator.PLATFORM_TENANT_ID.equals(operator.tenantId());
        Set<String> memberships = targetIsPlatform ? Set.of()
                : tenantScopeResolver.resolveEffectiveTenantScope(operator.internalId(), operator.tenantId());
        String tenant = resolveActingTenant(operator, targetIsPlatform, memberships, actingTenantHeader);

        // ADR-MONO-024 D2 — T ∈ the actor's operator.manage grant scope ('*' covers every T).
        tenantScopeGuard.requireTenantInScope(
                actor, Permission.OPERATOR_MANAGE, tenant, ActionCode.OPERATOR_ROLE_CHANGE);

        if (!targetIsPlatform) {
            if (AdminOperator.PLATFORM_TENANT_ID.equals(tenant)) {
                // ADR-082 D2 (D-14): a company operator never receives a '*' row.
                throw new IllegalArgumentException(
                        "A company operator cannot hold platform ('*') roles");
            }
            if (!memberships.contains(tenant)) {
                throw new OperatorNotFoundException(
                        "Operator not found for operatorId=" + operatorUuid + " in tenant " + tenant);
            }
        }

        Map<String, AdminOperatorPort.RoleView> resolvedRoles = operatorPort.resolveRolesByName(roleNames);

        // ADR-MONO-024 D3 grant-menu no-escalation, «own» = what the actor holds IN T (784 D-6).
        roleGrantGuard.requireGrantable(actor, resolvedRoles.values(), ActionCode.OPERATOR_ROLE_CHANGE, tenant);

        // Owner decision 2026-10-11 UTC: T keeps an ACTIVE TENANT_ADMIN after the replacement.
        long targetId = operator.internalId();
        String writtenTenant = tenant;
        boolean regrantsAdmin = resolvedRoles.containsKey(TenantLastAdminGuard.TENANT_ADMIN_ROLE)
                && AdminOperator.Status.ACTIVE.name().equals(operator.status());
        lastAdminGuard.requireAdminRemains(tenant,
                row -> row.operatorInternalId() == targetId && row.isDirect() && writtenTenant.equals(row.tenantId()),
                regrantsAdmin ? targetId : null);

        // The rows this edit does NOT own — T's group fan-out and org-node rows — stay, and are reported back.
        Set<String> keptInTenant = new TreeSet<>();
        for (AdminOperatorPort.RoleRowView row : operatorPort.findRoleRowsOfOperator(targetId)) {
            if (tenant.equals(row.tenantId()) && !row.isDirect() && row.roleName() != null) {
                keptInTenant.add(row.roleName());
            }
        }

        Instant now = Instant.now();
        Long actorInternalId = operatorPort.resolveActorInternalId(
                actor == null ? null : actor.operatorId());

        operatorPort.deleteDirectOperatorRolesInTenant(targetId, tenant);

        List<AdminOperatorPort.NewRoleBinding> bindings = new ArrayList<>(resolvedRoles.size());
        for (AdminOperatorPort.RoleView role : resolvedRoles.values()) {
            bindings.add(new AdminOperatorPort.NewRoleBinding(targetId, role.id(), now, actorInternalId, tenant));
        }
        operatorPort.saveOperatorRoles(bindings);

        String auditId = auditor.newAuditId();
        auditor.record(new AdminActionAuditor.AuditRecord(
                auditId,
                ActionCode.OPERATOR_ROLE_CHANGE,
                actor,
                "OPERATOR",
                operatorUuid,
                AuditReasons.normalize(reason),
                null,
                "roles:" + auditId,
                Outcome.SUCCESS,
                null,
                now,
                Instant.now(),
                tenant));

        safeInvalidatePermissionCache(operatorUuid);

        // Response: T's roles after the edit — the requested direct roles (request order) then T's group /
        // org-node roles (sorted). Another company's roles are never shown.
        List<String> rolesInTenant = new ArrayList<>(resolvedRoles.keySet());
        for (String kept : keptInTenant) {
            if (!rolesInTenant.contains(kept)) rolesInTenant.add(kept);
        }
        return new PatchRolesResult(operatorUuid, rolesInTenant, auditId);
    }

    /**
     * admin-api.md § PATCH …/roles «행동 테넌트 T» — the target, not the actor, resolves an omitted header (S1-1),
     * so a single-company target answers byte-identically to the pre-784 «home» edit.
     */
    private static String resolveActingTenant(AdminOperatorPort.OperatorView operator, boolean targetIsPlatform,
                                              Set<String> memberships, String header) {
        if (targetIsPlatform) {
            return AdminOperator.PLATFORM_TENANT_ID;
        }
        if (header != null && !header.isBlank()) {
            return header.trim();
        }
        if (memberships.size() == 1) {
            return memberships.iterator().next();
        }
        if (memberships.isEmpty()) {
            throw new OperatorNotFoundException(
                    "Operator " + operator.operatorId() + " belongs to no company");
        }
        throw new IllegalArgumentException(
                "X-Tenant-Id is required: the operator belongs to more than one company");
    }

    private void safeInvalidatePermissionCache(String operatorUuid) {
        if (cachingPermissionEvaluator == null) return;
        try {
            cachingPermissionEvaluator.invalidate(operatorUuid);
        } catch (RuntimeException ex) {
            log.warn("Permission cache invalidate failed for operatorId={} cause={}",
                    operatorUuid, ex.getClass().getSimpleName());
        }
    }

    public record PatchRolesResult(
            String operatorId,
            List<String> roles,
            String auditId
    ) {}
}
