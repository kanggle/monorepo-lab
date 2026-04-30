package com.example.admin.application;

import com.example.admin.application.exception.RoleNotFoundException;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorJpaEntity;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorJpaRepository;
import com.example.admin.infrastructure.persistence.rbac.AdminRoleJpaEntity;
import com.example.admin.infrastructure.persistence.rbac.AdminRoleJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Shared helpers for operator administration use cases.
 *
 * <p>Extracted from {@code CreateOperatorUseCase} and {@code PatchOperatorRoleUseCase}
 * (TASK-BE-121) to remove duplicated private helpers. Behaviour must remain identical
 * to the original inlined implementations:
 *
 * <ul>
 *   <li>{@link #resolveRoles(List)} — null/empty input returns an empty map; blank role
 *       names are skipped; unknown roles raise {@link RoleNotFoundException}; duplicate
 *       names collapse to the first occurrence (preserving insertion order).</li>
 *   <li>{@link #resolveActorInternalId(OperatorContext)} — null actor or null
 *       operatorId returns {@code null}; unresolved operator returns {@code null}.</li>
 *   <li>{@link #normalizeReason(String)} — null/blank → {@code "<not_provided>"}.</li>
 * </ul>
 *
 * <p>Package-private by design — only application-layer use cases collaborate with
 * this helper. Do not expose beyond the {@code application} package.
 */
@Component
@RequiredArgsConstructor
class OperatorRoleResolver {

    static final String REASON_NOT_PROVIDED = "<not_provided>";

    private final AdminOperatorJpaRepository operatorRepository;
    private final AdminRoleJpaRepository roleRepository;

    /**
     * Resolve role names to JPA entities preserving the requested ordering.
     *
     * @throws RoleNotFoundException when any non-blank requested role is unknown.
     */
    Map<String, AdminRoleJpaEntity> resolveRoles(List<String> roleNames) {
        if (roleNames == null || roleNames.isEmpty()) {
            return new LinkedHashMap<>();
        }
        LinkedHashMap<String, AdminRoleJpaEntity> out = new LinkedHashMap<>();
        List<AdminRoleJpaEntity> found = roleRepository.findByNameIn(roleNames);
        Map<String, AdminRoleJpaEntity> byName = new LinkedHashMap<>();
        for (AdminRoleJpaEntity r : found) {
            byName.put(r.getName(), r);
        }
        for (String name : roleNames) {
            if (name == null || name.isBlank()) continue;
            AdminRoleJpaEntity role = byName.get(name);
            if (role == null) {
                throw new RoleNotFoundException("Unknown role name: " + name);
            }
            out.putIfAbsent(name, role);
        }
        return out;
    }

    /**
     * Resolve the JPA-internal id of the actor performing the action, used as
     * {@code created_by} for new operator-role bindings.
     *
     * @return internal id, or {@code null} if the actor or its operatorId is missing
     *         / not present in the operator registry.
     */
    Long resolveActorInternalId(OperatorContext actor) {
        if (actor == null || actor.operatorId() == null) return null;
        Optional<AdminOperatorJpaEntity> found = operatorRepository.findByOperatorId(actor.operatorId());
        return found.map(AdminOperatorJpaEntity::getId).orElse(null);
    }

    /**
     * Normalise a free-form audit reason to the placeholder used when callers omit
     * the value, so the audit log never carries an empty reason.
     */
    static String normalizeReason(String reason) {
        return (reason == null || reason.isBlank()) ? REASON_NOT_PROVIDED : reason;
    }
}
