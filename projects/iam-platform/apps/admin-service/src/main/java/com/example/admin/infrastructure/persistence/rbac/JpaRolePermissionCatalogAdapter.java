package com.example.admin.infrastructure.persistence.rbac;

import com.example.admin.application.port.RolePermissionCatalogPort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * TASK-BE-486 — JPA-backed adapter for {@link RolePermissionCatalogPort}. Wraps
 * {@code AdminRoleJpaRepository} + {@code AdminRolePermissionJpaRepository} and
 * projects the entities to {@link RoleWithPermissions} so the application layer
 * never sees JPA types.
 */
@Component
@RequiredArgsConstructor
public class JpaRolePermissionCatalogAdapter implements RolePermissionCatalogPort {

    private final AdminRoleJpaRepository roleRepository;
    private final AdminRolePermissionJpaRepository rolePermissionRepository;

    @Override
    public List<RoleWithPermissions> findAllRolesWithPermissions() {
        // Stable seed order — matches GET /api/admin/operators/grantable-roles.
        List<AdminRoleJpaEntity> roles = roleRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));
        if (roles.isEmpty()) {
            return List.of();
        }

        List<Long> roleIds = new ArrayList<>(roles.size());
        for (AdminRoleJpaEntity r : roles) {
            roleIds.add(r.getId());
        }

        // One query for all bindings → group by role id (no N+1).
        Map<Long, List<String>> keysByRole = new LinkedHashMap<>();
        for (AdminRolePermissionJpaEntity p : rolePermissionRepository.findByRoleIdIn(roleIds)) {
            keysByRole.computeIfAbsent(p.getRoleId(), k -> new ArrayList<>()).add(p.getPermissionKey());
        }

        List<RoleWithPermissions> out = new ArrayList<>(roles.size());
        for (AdminRoleJpaEntity r : roles) {
            List<String> keys = keysByRole.getOrDefault(r.getId(), new ArrayList<>());
            Collections.sort(keys); // deterministic per-role ordering
            out.add(new RoleWithPermissions(
                    r.getId(), r.getName(), r.getDescription(), List.copyOf(keys)));
        }
        return out;
    }
}
