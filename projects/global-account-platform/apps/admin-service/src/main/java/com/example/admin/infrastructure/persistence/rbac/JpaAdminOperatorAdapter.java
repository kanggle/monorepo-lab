package com.example.admin.infrastructure.persistence.rbac;

import com.example.admin.application.exception.OperatorEmailConflictException;
import com.example.admin.application.exception.OperatorNotFoundException;
import com.example.admin.application.exception.RoleNotFoundException;
import com.example.admin.application.port.AdminOperatorPort;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * TASK-BE-288 — JPA-backed adapter for {@link AdminOperatorPort}. Wraps the
 * three RBAC repositories ({@code AdminOperatorJpaRepository},
 * {@code AdminRoleJpaRepository}, {@code AdminOperatorRoleJpaRepository}) and
 * projects entities to {@link OperatorView} / {@link RoleView} so the
 * application layer never sees JPA types.
 */
@Component
@RequiredArgsConstructor
public class JpaAdminOperatorAdapter implements AdminOperatorPort {

    private final AdminOperatorJpaRepository operatorRepository;
    private final AdminRoleJpaRepository roleRepository;
    private final AdminOperatorRoleJpaRepository operatorRoleRepository;

    // ---------- Operator ----------

    @Override
    public Optional<OperatorView> findByOperatorId(String operatorUuid) {
        return operatorRepository.findByOperatorId(operatorUuid).map(JpaAdminOperatorAdapter::toView);
    }

    @Override
    public Optional<OperatorView> findByOidcSubject(String oidcSubject) {
        if (oidcSubject == null || oidcSubject.isBlank()) {
            // Defensive: a blank/null subject never maps to a provisioned
            // operator. Empty == fail-closed branch (no token minted).
            return Optional.empty();
        }
        return operatorRepository.findByOidcSubject(oidcSubject).map(JpaAdminOperatorAdapter::toView);
    }

    @Override
    public boolean existsByTenantIdAndEmail(String tenantId, String email) {
        return operatorRepository.existsByTenantIdAndEmail(tenantId, email);
    }

    @Override
    public OperatorPage findOperatorsPage(String statusFilter, int page, int size) {
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");
        PageRequest pageable = PageRequest.of(page, size, sort);
        Page<AdminOperatorJpaEntity> rows;
        if (statusFilter == null || statusFilter.isBlank()) {
            rows = operatorRepository.findAll(pageable);
        } else {
            rows = operatorRepository.findByStatus(statusFilter, pageable);
        }
        List<OperatorView> content = new ArrayList<>(rows.getNumberOfElements());
        for (AdminOperatorJpaEntity e : rows.getContent()) content.add(toView(e));
        return new OperatorPage(content, rows.getTotalElements(), rows.getNumber(),
                rows.getSize(), rows.getTotalPages());
    }

    @Override
    public OperatorView createOperator(NewOperator row) {
        AdminOperatorJpaEntity entity = AdminOperatorJpaEntity.create(
                row.operatorId(), row.email(), row.passwordHash(), row.displayName(),
                row.status(), row.tenantId(), row.createdAt());
        try {
            entity = operatorRepository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException ex) {
            throw new OperatorEmailConflictException("Operator email already exists");
        }
        return toView(entity);
    }

    @Override
    public void changeStatus(long operatorInternalId, String newStatus, Instant at) {
        AdminOperatorJpaEntity entity = operatorRepository.findById(operatorInternalId)
                .orElseThrow(() -> new OperatorNotFoundException(
                        "admin_operators row not found for internalId=" + operatorInternalId));
        entity.changeStatus(newStatus, at);
        operatorRepository.save(entity);
    }

    @Override
    public void changePasswordHash(long operatorInternalId, String newPasswordHash, Instant at) {
        AdminOperatorJpaEntity entity = operatorRepository.findById(operatorInternalId)
                .orElseThrow(() -> new OperatorNotFoundException(
                        "admin_operators row not found for internalId=" + operatorInternalId));
        entity.changePasswordHash(newPasswordHash, at);
        operatorRepository.save(entity);
    }

    // ---------- Roles ----------

    @Override
    public Map<String, RoleView> resolveRolesByName(List<String> roleNames) {
        if (roleNames == null || roleNames.isEmpty()) {
            return new LinkedHashMap<>();
        }
        LinkedHashMap<String, RoleView> out = new LinkedHashMap<>();
        List<AdminRoleJpaEntity> found = roleRepository.findByNameIn(roleNames);
        Map<String, AdminRoleJpaEntity> byName = new LinkedHashMap<>();
        for (AdminRoleJpaEntity r : found) byName.put(r.getName(), r);
        for (String name : roleNames) {
            if (name == null || name.isBlank()) continue;
            AdminRoleJpaEntity role = byName.get(name);
            if (role == null) {
                throw new RoleNotFoundException("Unknown role name: " + name);
            }
            out.putIfAbsent(name, toView(role));
        }
        return out;
    }

    @Override
    public List<RoleView> findRolesForOperator(long operatorInternalId) {
        List<AdminOperatorRoleJpaEntity> bindings = operatorRoleRepository.findByOperatorId(operatorInternalId);
        if (bindings.isEmpty()) return List.of();
        List<Long> roleIds = new ArrayList<>(bindings.size());
        for (AdminOperatorRoleJpaEntity b : bindings) roleIds.add(b.getRoleId());
        List<AdminRoleJpaEntity> roles = roleRepository.findAllById(roleIds);
        List<RoleView> out = new ArrayList<>(roles.size());
        for (AdminRoleJpaEntity r : roles) out.add(toView(r));
        return out;
    }

    @Override
    public boolean anyRoleRequires2fa(long operatorInternalId) {
        List<AdminOperatorRoleJpaEntity> bindings = operatorRoleRepository.findByOperatorId(operatorInternalId);
        if (bindings.isEmpty()) return false;
        List<Long> roleIds = new ArrayList<>(bindings.size());
        for (AdminOperatorRoleJpaEntity b : bindings) roleIds.add(b.getRoleId());
        return roleRepository.findAllById(roleIds).stream().anyMatch(AdminRoleJpaEntity::isRequire2fa);
    }

    @Override
    public Map<Long, List<String>> bulkLoadRoleNamesByOperator(Collection<Long> operatorInternalIds) {
        if (operatorInternalIds == null || operatorInternalIds.isEmpty()) return Map.of();
        List<AdminOperatorRoleJpaEntity> bindings = operatorRoleRepository.findByOperatorIdIn(operatorInternalIds);
        if (bindings.isEmpty()) return Map.of();

        List<Long> roleIds = new ArrayList<>(bindings.size());
        for (AdminOperatorRoleJpaEntity b : bindings) roleIds.add(b.getRoleId());
        Map<Long, String> roleNameById = new LinkedHashMap<>();
        for (AdminRoleJpaEntity r : roleRepository.findAllById(roleIds)) {
            roleNameById.put(r.getId(), r.getName());
        }

        Map<Long, List<String>> byOperator = new LinkedHashMap<>();
        for (AdminOperatorRoleJpaEntity b : bindings) {
            String roleName = roleNameById.get(b.getRoleId());
            if (roleName == null) continue;
            byOperator.computeIfAbsent(b.getOperatorId(), k -> new ArrayList<>()).add(roleName);
        }
        for (List<String> names : byOperator.values()) Collections.sort(names);
        return byOperator;
    }

    // ---------- Operator-Role bindings ----------

    @Override
    public void deleteOperatorRoles(long operatorInternalId) {
        operatorRoleRepository.deleteByOperatorId(operatorInternalId);
    }

    @Override
    public void saveOperatorRoles(List<NewRoleBinding> bindings) {
        if (bindings == null || bindings.isEmpty()) return;
        List<AdminOperatorRoleJpaEntity> entities = new ArrayList<>(bindings.size());
        for (NewRoleBinding b : bindings) {
            entities.add(AdminOperatorRoleJpaEntity.create(
                    b.operatorInternalId(), b.roleId(), b.grantedAt(), b.grantedBy(), b.tenantId()));
        }
        operatorRoleRepository.saveAll(entities);
    }

    // ---------- Helpers ----------

    @Override
    public Long resolveActorInternalId(String operatorUuid) {
        if (operatorUuid == null || operatorUuid.isBlank()) return null;
        return operatorRepository.findByOperatorId(operatorUuid)
                .map(AdminOperatorJpaEntity::getId)
                .orElse(null);
    }

    // ---------- Mappers ----------

    private static OperatorView toView(AdminOperatorJpaEntity e) {
        return new OperatorView(
                e.getId(),
                e.getOperatorId(),
                e.getTenantId(),
                e.getEmail(),
                e.getPasswordHash(),
                e.getDisplayName(),
                e.getStatus(),
                e.getTotpEnrolledAt(),
                e.getLastLoginAt(),
                e.getCreatedAt(),
                e.getUpdatedAt());
    }

    private static RoleView toView(AdminRoleJpaEntity r) {
        return new RoleView(r.getId(), r.getName(), r.getDescription(), r.isRequire2fa());
    }
}
