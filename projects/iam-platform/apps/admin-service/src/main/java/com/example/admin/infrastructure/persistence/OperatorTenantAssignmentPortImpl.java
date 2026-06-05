package com.example.admin.infrastructure.persistence;

import com.example.admin.application.port.OperatorTenantAssignmentPort;
import com.example.admin.infrastructure.persistence.rbac.OperatorTenantAssignmentJpaEntity;
import com.example.admin.infrastructure.persistence.rbac.OperatorTenantAssignmentJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
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

    @Override
    @Transactional(readOnly = true)
    public Optional<AssignmentView> findAssignment(Long operatorInternalId, String tenantId) {
        if (operatorInternalId == null || tenantId == null || tenantId.isBlank()) {
            return Optional.empty();
        }
        return repository.findByOperatorIdAndTenantId(operatorInternalId, tenantId)
                .map(e -> new AssignmentView(e.getTenantId(), e.getOrgScope(), e.getPermissionSetId()));
    }

    @Override
    @Transactional
    public void updateOrgScope(Long operatorInternalId, String tenantId, List<String> orgScope) {
        // The caller (use case) has already verified the row exists; if a
        // concurrent delete removed it, treat as a no-op (the use case's
        // ASSIGNMENT_NOT_FOUND gate is the authoritative pre-check).
        repository.findByOperatorIdAndTenantId(operatorInternalId, tenantId)
                .ifPresent(entity -> {
                    entity.setOrgScope(orgScope);
                    // BE-335 lesson: explicit flush — a dirty UPDATE must be
                    // flushed, never deferred to commit-time auto-flush.
                    repository.saveAndFlush(entity);
                });
    }
}
