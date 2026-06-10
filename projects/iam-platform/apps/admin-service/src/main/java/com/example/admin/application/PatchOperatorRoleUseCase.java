package com.example.admin.application;

import com.example.admin.application.exception.OperatorNotFoundException;
import com.example.admin.application.port.AdminOperatorPort;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class PatchOperatorRoleUseCase {

    private final AdminOperatorPort operatorPort;
    private final AdminActionAuditor auditor;
    private final CachingPermissionEvaluator cachingPermissionEvaluator;
    private final TenantScopeGuard tenantScopeGuard;

    @Transactional
    public PatchRolesResult patchRoles(String operatorUuid,
                                       List<String> roleNames,
                                       OperatorContext actor,
                                       String reason) {
        AdminOperatorPort.OperatorView operator = operatorPort.findByOperatorId(operatorUuid)
                .orElseThrow(() -> new OperatorNotFoundException(
                        "Operator not found for operatorId=" + operatorUuid));

        // ADR-MONO-024 D2 (TASK-BE-345): the actor must hold operator.manage scoped
        // to the managed operator's home tenant. Net-zero for SUPER_ADMIN ('*').
        tenantScopeGuard.requireTenantInScope(
                actor, Permission.OPERATOR_MANAGE, operator.tenantId(), ActionCode.OPERATOR_ROLE_CHANGE);

        Map<String, AdminOperatorPort.RoleView> resolvedRoles = operatorPort.resolveRolesByName(roleNames);

        Instant now = Instant.now();
        Long actorInternalId = operatorPort.resolveActorInternalId(
                actor == null ? null : actor.operatorId());

        operatorPort.deleteOperatorRoles(operator.internalId());

        List<AdminOperatorPort.NewRoleBinding> bindings = new ArrayList<>(resolvedRoles.size());
        for (AdminOperatorPort.RoleView role : resolvedRoles.values()) {
            // TASK-BE-289 WI-2: bind the target operator's own tenant_id
            // (per-tenant binding invariant — data-model.md §admin_operator_roles).
            // Mirrors CreateOperatorUseCase; closes TASK-BE-288 review Finding 1.
            bindings.add(new AdminOperatorPort.NewRoleBinding(
                    operator.internalId(), role.id(), now, actorInternalId, operator.tenantId()));
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
                Instant.now()));

        safeInvalidatePermissionCache(operatorUuid);

        List<String> orderedRoles = new ArrayList<>(resolvedRoles.keySet());
        return new PatchRolesResult(operatorUuid, orderedRoles, auditId);
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
