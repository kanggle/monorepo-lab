package com.example.admin.infrastructure.persistence.rbac;

import com.example.admin.domain.rbac.AdminOperator;
import com.example.admin.domain.rbac.PermissionEvaluator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * DB-backed permission evaluator. Resolves:
 *   admin_operators (status=ACTIVE) → admin_operator_roles → admin_role_permissions
 * on every invocation.
 *
 * <p>Since TASK-BE-028c this is the <em>origin</em> (no cache) — the Redis-backed
 * 10s TTL cache is applied by {@code CachingPermissionEvaluator}, which is the
 * {@code @Primary} {@link PermissionEvaluator} bean and delegates to this class
 * on miss / degrade.
 *
 * <p>Fail-closed: any unexpected exception during evaluation is treated as deny
 * and logged for operational triage.
 */
@Slf4j
@Component("originPermissionEvaluator")
@RequiredArgsConstructor
public class PermissionEvaluatorImpl implements PermissionEvaluator {

    private final AdminOperatorJpaRepository operators;
    private final AdminOperatorRoleJpaRepository operatorRoles;
    private final AdminRolePermissionJpaRepository rolePermissions;

    @Override
    @Transactional(readOnly = true)
    public boolean hasPermission(String operatorId, String permission) {
        if (operatorId == null || permission == null) {
            return false;
        }
        try {
            Set<String> perms = loadPermissions(operatorId);
            return perms.contains(permission);
        } catch (RuntimeException ex) {
            log.error("Permission evaluation failed (fail-closed) operatorId={} permission={}",
                    operatorId, permission, ex);
            return false;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasAllPermissions(String operatorId, Collection<String> permissions) {
        if (operatorId == null || permissions == null || permissions.isEmpty()) {
            return false;
        }
        try {
            Set<String> perms = loadPermissions(operatorId);
            return perms.containsAll(permissions);
        } catch (RuntimeException ex) {
            log.error("Permission evaluation failed (fail-closed) operatorId={} permissions={}",
                    operatorId, permissions, ex);
            return false;
        }
    }

    /**
     * Public so the caching decorator can fetch the canonical permission set
     * on cache miss without re-implementing the join logic.
     */
    @Transactional(readOnly = true)
    public Set<String> loadPermissions(String operatorId) {
        // operatorId is the external UUID (JWT `sub`). Translate to the internal
        // BIGINT PK before joining role/permission tables (TASK-BE-028b1).
        Optional<AdminOperatorJpaEntity> op = operators.findByOperatorId(operatorId);
        if (op.isEmpty()) {
            return Set.of();
        }
        AdminOperatorJpaEntity operator = op.get();
        String status = operator.getStatus();
        if (!AdminOperator.Status.ACTIVE.name().equals(status)) {
            return Set.of();
        }
        List<Long> roleIds = operatorRoles.findByOperatorId(operator.getId()).stream()
                .map(AdminOperatorRoleJpaEntity::getRoleId)
                .toList();
        if (roleIds.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(rolePermissions.findPermissionKeysByRoleIds(roleIds));
    }
}
