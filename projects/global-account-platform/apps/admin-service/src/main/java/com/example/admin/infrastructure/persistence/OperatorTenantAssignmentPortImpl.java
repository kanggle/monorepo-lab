package com.example.admin.infrastructure.persistence;

import com.example.admin.application.port.OperatorTenantAssignmentPort;
import com.example.admin.infrastructure.persistence.rbac.OperatorTenantAssignmentJpaEntity;
import com.example.admin.infrastructure.persistence.rbac.OperatorTenantAssignmentJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * TASK-BE-326 / ADR-MONO-020 D1 — JPA-backed {@link OperatorTenantAssignmentPort}.
 * Keeps {@code operator_tenant_assignment} JPA types out of the application layer.
 */
@Component
@RequiredArgsConstructor
public class OperatorTenantAssignmentPortImpl implements OperatorTenantAssignmentPort {

    private final OperatorTenantAssignmentJpaRepository repository;

    @Override
    @Transactional(readOnly = true)
    public Set<String> findAssignedTenantIds(Long operatorInternalId) {
        if (operatorInternalId == null) {
            return Set.of();
        }
        Set<String> tenantIds = new LinkedHashSet<>();
        for (OperatorTenantAssignmentJpaEntity e : repository.findByOperatorId(operatorInternalId)) {
            if (e.getTenantId() != null) {
                tenantIds.add(e.getTenantId());
            }
        }
        return tenantIds;
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> findOrgScope(Long operatorInternalId, String tenantId) {
        if (operatorInternalId == null || tenantId == null || tenantId.isBlank()) {
            // No explicit assignment row → null ⟺ ["*"] (net-zero default).
            return null;
        }
        // null when no explicit assignment row OR the column is unset (NULL).
        // An explicit empty list ([]) is returned verbatim (zero-scope, NOT widened).
        return repository.findByOperatorIdAndTenantId(operatorInternalId, tenantId)
                .map(OperatorTenantAssignmentJpaEntity::getOrgScope)
                .orElse(null);
    }
}
