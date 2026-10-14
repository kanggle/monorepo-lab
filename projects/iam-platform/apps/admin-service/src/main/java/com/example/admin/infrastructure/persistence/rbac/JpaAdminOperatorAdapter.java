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
    public boolean existsMemberWithEmail(String tenantId, String email, Long excludeOperatorInternalId) {
        if (tenantId == null || tenantId.isBlank() || email == null || email.isBlank()) {
            return false;
        }
        return operatorRepository.existsMemberWithEmail(tenantId, email, excludeOperatorInternalId);
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
    public OperatorPage findOperatorsPageByTenant(String tenantId, String statusFilter, int page, int size) {
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");
        PageRequest pageable = PageRequest.of(page, size, sort);
        String status = (statusFilter == null || statusFilter.isBlank()) ? null : statusFilter;
        Page<AdminOperatorJpaEntity> rows =
                operatorRepository.findByTenantScope(tenantId, status, pageable);
        List<OperatorView> content = new ArrayList<>(rows.getNumberOfElements());
        for (AdminOperatorJpaEntity e : rows.getContent()) content.add(toView(e));
        return new OperatorPage(content, rows.getTotalElements(), rows.getNumber(),
                rows.getSize(), rows.getTotalPages());
    }

    @Override
    public List<OperatorLookupView> findLookupCandidates(String tenantId, String email) {
        boolean anyTenant = "*".equals(tenantId);
        List<AdminOperatorJpaEntity> rows = anyTenant
                ? operatorRepository.findLookupCandidatesAnyTenant(email)
                : operatorRepository.findLookupCandidatesInTenant(tenantId, email);
        List<OperatorLookupView> out = new ArrayList<>(rows.size());
        for (AdminOperatorJpaEntity e : rows) {
            // TASK-MONO-751: a confined operator can assume only its confined tenant.
            if (!anyTenant && e.getConfinedTenantId() != null
                    && !e.getConfinedTenantId().equals(tenantId)) {
                continue;
            }
            out.add(new OperatorLookupView(e.getOidcSubject(), e.getDisplayName(), e.getTenantId()));
        }
        out.sort(java.util.Comparator.comparing(OperatorLookupView::tenantId));
        return out;
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
    public List<OperatorOidcSubjectView> findOperatorsWithOidcSubject() {
        List<AdminOperatorJpaEntity> rows = operatorRepository.findByOidcSubjectIsNotNull();
        List<OperatorOidcSubjectView> out = new ArrayList<>(rows.size());
        for (AdminOperatorJpaEntity e : rows) {
            out.add(new OperatorOidcSubjectView(
                    e.getId(), e.getOperatorId(), e.getTenantId(), e.getOidcSubject()));
        }
        return out;
    }

    @Override
    public boolean existsOperatorBySubject(String accountId) {
        return accountId != null && !accountId.isBlank()
                && operatorRepository.existsByOidcSubject(accountId);
    }

    @Override
    public boolean existsOperatorByIdentity(String identityId) {
        return identityId != null && !identityId.isBlank()
                && operatorRepository.existsByIdentityId(identityId);
    }

    @Override
    public void updateOidcSubject(long operatorInternalId, String newOidcSubject, Instant at) {
        AdminOperatorJpaEntity entity = operatorRepository.findById(operatorInternalId)
                .orElseThrow(() -> new OperatorNotFoundException(
                        "admin_operators row not found for internalId=" + operatorInternalId));
        entity.setOidcSubject(newOidcSubject, at);
        operatorRepository.saveAndFlush(entity); // TASK-BE-335: explicit flush (see changeStatus)
    }

    @Override
    public void changeStatus(long operatorInternalId, String newStatus, Instant at) {
        AdminOperatorJpaEntity entity = operatorRepository.findById(operatorInternalId)
                .orElseThrow(() -> new OperatorNotFoundException(
                        "admin_operators row not found for internalId=" + operatorInternalId));
        entity.changeStatus(newStatus, at);
        // TASK-BE-335: saveAndFlush (NOT save) — force the managed-entity dirty
        // UPDATE to flush WITHIN this transaction. The request's main tx skips
        // the commit-time auto-flush (readOnly flush-mode / OSIV), which silently
        // dropped this UPDATE while the request still returned 200 (the
        // REQUIRES_NEW audit row + an IDENTITY-immediate create masked it).
        // An explicit flush executes even under FlushMode.MANUAL.
        operatorRepository.saveAndFlush(entity);
    }

    @Override
    public void changePasswordHash(long operatorInternalId, String newPasswordHash, Instant at) {
        AdminOperatorJpaEntity entity = operatorRepository.findById(operatorInternalId)
                .orElseThrow(() -> new OperatorNotFoundException(
                        "admin_operators row not found for internalId=" + operatorInternalId));
        entity.changePasswordHash(newPasswordHash, at);
        operatorRepository.saveAndFlush(entity); // TASK-BE-335: explicit flush (see changeStatus)
    }

    @Override
    public void changeFinanceDefaultAccountId(long operatorInternalId, String newValue, Instant at) {
        AdminOperatorJpaEntity entity = operatorRepository.findById(operatorInternalId)
                .orElseThrow(() -> new OperatorNotFoundException(
                        "admin_operators row not found for internalId=" + operatorInternalId));
        entity.changeFinanceDefaultAccountId(newValue, at);
        operatorRepository.saveAndFlush(entity); // TASK-BE-335: explicit flush (see changeStatus)
    }

    @Override
    public void linkIdentity(long operatorInternalId, String identityId, Instant at) {
        AdminOperatorJpaEntity entity = operatorRepository.findById(operatorInternalId)
                .orElseThrow(() -> new OperatorNotFoundException(
                        "admin_operators row not found for internalId=" + operatorInternalId));
        entity.linkIdentity(identityId, at);
        operatorRepository.saveAndFlush(entity); // TASK-BE-335: explicit flush (see changeStatus)
    }

    @Override
    public void unlinkIdentity(long operatorInternalId, Instant at) {
        AdminOperatorJpaEntity entity = operatorRepository.findById(operatorInternalId)
                .orElseThrow(() -> new OperatorNotFoundException(
                        "admin_operators row not found for internalId=" + operatorInternalId));
        entity.unlinkIdentity(at);
        operatorRepository.saveAndFlush(entity); // TASK-BE-335: explicit flush (see changeStatus)
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
    public List<RoleView> findAllRoles() {
        List<AdminRoleJpaEntity> roles = roleRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));
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
            // TASK-BE-633 (V0053): one row per SOURCE (and, since 784, per company) — the same role from direct +
            // a group, or in two companies, is listed once.
            List<String> names = byOperator.computeIfAbsent(b.getOperatorId(), k -> new ArrayList<>());
            if (!names.contains(roleName)) names.add(roleName);
        }
        for (List<String> names : byOperator.values()) Collections.sort(names);
        return byOperator;
    }

    @Override
    public Map<Long, List<String>> bulkLoadRoleNamesByOperatorInTenant(Collection<Long> operatorInternalIds,
                                                                      String tenantId) {
        if (operatorInternalIds == null || operatorInternalIds.isEmpty() || tenantId == null) return Map.of();
        List<AdminOperatorRoleJpaEntity> bindings = new ArrayList<>();
        for (AdminOperatorRoleJpaEntity b : operatorRoleRepository.findByOperatorIdIn(operatorInternalIds)) {
            if (tenantId.equals(b.getTenantId())) bindings.add(b);
        }
        if (bindings.isEmpty()) return Map.of();
        Map<Long, String> roleNameById = roleNamesById(bindings);
        Map<Long, List<String>> byOperator = new LinkedHashMap<>();
        for (AdminOperatorRoleJpaEntity b : bindings) {
            String roleName = roleNameById.get(b.getRoleId());
            if (roleName == null) continue;
            List<String> names = byOperator.computeIfAbsent(b.getOperatorId(), k -> new ArrayList<>());
            if (!names.contains(roleName)) names.add(roleName);
        }
        for (List<String> names : byOperator.values()) Collections.sort(names);
        return byOperator;
    }

    @Override
    public Map<String, List<String>> findRoleNamesByTenant(long operatorInternalId) {
        List<AdminOperatorRoleJpaEntity> bindings = operatorRoleRepository.findByOperatorId(operatorInternalId);
        if (bindings.isEmpty()) return Map.of();
        Map<Long, String> roleNameById = roleNamesById(bindings);
        Map<String, List<String>> byTenant = new java.util.TreeMap<>();
        for (AdminOperatorRoleJpaEntity b : bindings) {
            String roleName = roleNameById.get(b.getRoleId());
            if (roleName == null || b.getTenantId() == null) continue;
            List<String> names = byTenant.computeIfAbsent(b.getTenantId(), k -> new ArrayList<>());
            if (!names.contains(roleName)) names.add(roleName);
        }
        for (List<String> names : byTenant.values()) Collections.sort(names);
        return byTenant;
    }

    private Map<Long, String> roleNamesById(List<AdminOperatorRoleJpaEntity> bindings) {
        List<Long> roleIds = new ArrayList<>(bindings.size());
        for (AdminOperatorRoleJpaEntity b : bindings) roleIds.add(b.getRoleId());
        Map<Long, String> roleNameById = new LinkedHashMap<>();
        for (AdminRoleJpaEntity r : roleRepository.findAllById(roleIds)) {
            roleNameById.put(r.getId(), r.getName());
        }
        return roleNameById;
    }

    // ---------- Operator-Role bindings ----------

    @Override
    public List<RoleRowView> findRoleRowsOfOperator(long operatorInternalId) {
        return toRowViews(operatorRoleRepository.findByOperatorId(operatorInternalId));
    }

    @Override
    public List<RoleRowView> findActiveRoleRowsInTenant(String tenantId, String roleName) {
        if (tenantId == null || roleName == null) return List.of();
        return toRowViews(operatorRoleRepository.findActiveByTenantIdAndRoleName(tenantId, roleName));
    }

    @Override
    public int deleteDirectOperatorRolesInTenant(long operatorInternalId, String tenantId) {
        return operatorRoleRepository.deleteDirectByOperatorIdAndTenantId(operatorInternalId, tenantId);
    }

    @Override
    public int deleteOperatorRolesInTenant(long operatorInternalId, String tenantId) {
        return operatorRoleRepository.deleteByOperatorIdAndTenantId(operatorInternalId, tenantId);
    }

    @Override
    public int deleteOperatorRolesByGroupOrigin(long operatorInternalId, long groupInternalId) {
        return operatorRoleRepository.deleteByGroupOriginAndOperatorId(groupInternalId, operatorInternalId);
    }

    private List<RoleRowView> toRowViews(List<AdminOperatorRoleJpaEntity> rows) {
        if (rows.isEmpty()) return List.of();
        Map<Long, String> roleNameById = roleNamesById(rows);
        List<RoleRowView> out = new ArrayList<>(rows.size());
        for (AdminOperatorRoleJpaEntity r : rows) {
            out.add(new RoleRowView(r.getOperatorId(), r.getRoleId(), roleNameById.get(r.getRoleId()),
                    r.getTenantId(), r.getGroupOrigin(), r.getOrgNodeId()));
        }
        return out;
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
                e.getUpdatedAt(),
                e.getFinanceDefaultAccountId(),
                e.getIdentityId(),
                e.getConfinedTenantId());
    }

    private static RoleView toView(AdminRoleJpaEntity r) {
        return new RoleView(r.getId(), r.getName(), r.getDescription(), r.isRequire2fa());
    }
}
