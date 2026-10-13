package com.example.admin.application;

import com.example.admin.application.exception.RoleNotFoundException;
import com.example.admin.application.port.AdminOperatorPort;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * TASK-MONO-784 S4 — an in-memory {@code admin_operators} + {@code admin_operator_roles} for the write-axis unit
 * tests. A Mockito mock cannot show that a write left another company's rows BYTE-UNCHANGED (an unstubbed delete
 * is a silent no-op, so «delete every row of the operator» would look green); this fake really deletes, so the
 * «B rows byte-unchanged» cells can bite. Methods the write axis does not use throw.
 */
final class InMemoryRoleRowsPort implements AdminOperatorPort {

    /** One mutable role row — {@code grantedAt} is part of the «byte-unchanged» comparison. */
    record Row(long operatorId, long roleId, String roleName, String tenantId, Long groupOrigin, String orgNodeId,
               Instant grantedAt) {
        boolean isDirect() {
            return groupOrigin == null && orgNodeId == null;
        }
    }

    final Map<String, OperatorView> operators = new LinkedHashMap<>();
    final Map<String, RoleView> roles = new LinkedHashMap<>();
    final List<Row> rows = new ArrayList<>();

    InMemoryRoleRowsPort role(long id, String name) {
        roles.put(name, new RoleView(id, name, name, false));
        return this;
    }

    InMemoryRoleRowsPort operator(OperatorView op) {
        operators.put(op.operatorId(), op);
        return this;
    }

    InMemoryRoleRowsPort row(long operatorId, String roleName, String tenantId, Long groupOrigin, String orgNodeId) {
        RoleView r = roles.get(roleName);
        rows.add(new Row(operatorId, r.id(), roleName, tenantId, groupOrigin, orgNodeId,
                Instant.parse("2026-01-01T00:00:00Z")));
        return this;
    }

    /** The rows of one operator outside {@code tenantId} — the «other companies» snapshot. */
    List<Row> rowsOutside(long operatorId, String tenantId) {
        return rows.stream().filter(r -> r.operatorId() == operatorId && !tenantId.equals(r.tenantId())).toList();
    }

    List<Row> rowsIn(long operatorId, String tenantId) {
        return rows.stream().filter(r -> r.operatorId() == operatorId && tenantId.equals(r.tenantId())).toList();
    }

    // ---------- used by the write axis ----------

    @Override
    public Optional<OperatorView> findByOperatorId(String operatorUuid) {
        return Optional.ofNullable(operators.get(operatorUuid));
    }

    @Override
    public Map<String, RoleView> resolveRolesByName(List<String> roleNames) {
        Map<String, RoleView> out = new LinkedHashMap<>();
        if (roleNames == null) return out;
        for (String name : roleNames) {
            if (name == null || name.isBlank()) continue;
            RoleView r = roles.get(name);
            if (r == null) throw new RoleNotFoundException("Unknown role name: " + name);
            out.putIfAbsent(name, r);
        }
        return out;
    }

    @Override
    public List<RoleRowView> findRoleRowsOfOperator(long operatorInternalId) {
        return rows.stream().filter(r -> r.operatorId() == operatorInternalId).map(InMemoryRoleRowsPort::view)
                .toList();
    }

    @Override
    public List<RoleRowView> findActiveRoleRowsInTenant(String tenantId, String roleName) {
        List<RoleRowView> out = new ArrayList<>();
        for (Row r : rows) {
            if (!tenantId.equals(r.tenantId()) || !roleName.equals(r.roleName())) continue;
            boolean active = operators.values().stream()
                    .anyMatch(o -> o.internalId() == r.operatorId() && "ACTIVE".equals(o.status()));
            if (active) out.add(view(r));
        }
        return out;
    }

    @Override
    public int deleteDirectOperatorRolesInTenant(long operatorInternalId, String tenantId) {
        int before = rows.size();
        rows.removeIf(r -> r.operatorId() == operatorInternalId && tenantId.equals(r.tenantId()) && r.isDirect());
        return before - rows.size();
    }

    @Override
    public int deleteOperatorRolesInTenant(long operatorInternalId, String tenantId) {
        int before = rows.size();
        rows.removeIf(r -> r.operatorId() == operatorInternalId && tenantId.equals(r.tenantId()));
        return before - rows.size();
    }

    @Override
    public void saveOperatorRoles(List<NewRoleBinding> bindings) {
        for (NewRoleBinding b : bindings) {
            String name = roles.values().stream().filter(r -> r.id() == b.roleId()).map(RoleView::name)
                    .findFirst().orElseThrow();
            boolean dup = rows.stream().anyMatch(r -> r.operatorId() == b.operatorInternalId()
                    && r.roleId() == b.roleId() && r.tenantId().equals(b.tenantId()));
            if (dup) throw new IllegalStateException("duplicate PK (operator, role, tenant)");
            rows.add(new Row(b.operatorInternalId(), b.roleId(), name, b.tenantId(), null, null, b.grantedAt()));
        }
    }

    @Override
    public int deleteOperatorRolesByGroupOrigin(long operatorInternalId, long groupInternalId) {
        int before = rows.size();
        rows.removeIf(r -> r.operatorId() == operatorInternalId && r.groupOrigin() != null
                && r.groupOrigin() == groupInternalId);
        return before - rows.size();
    }

    @Override
    public Map<String, List<String>> findRoleNamesByTenant(long operatorInternalId) {
        Map<String, List<String>> out = new TreeMap<>();
        for (Row r : rows) {
            if (r.operatorId() != operatorInternalId) continue;
            List<String> names = out.computeIfAbsent(r.tenantId(), k -> new ArrayList<>());
            if (!names.contains(r.roleName())) names.add(r.roleName());
        }
        out.values().forEach(java.util.Collections::sort);
        return out;
    }

    @Override
    public Long resolveActorInternalId(String operatorUuid) {
        OperatorView op = operators.get(operatorUuid);
        return op == null ? null : op.internalId();
    }

    private static RoleRowView view(Row r) {
        return new RoleRowView(r.operatorId(), r.roleId(), r.roleName(), r.tenantId(), r.groupOrigin(),
                r.orgNodeId());
    }

    // ---------- not used by the write axis ----------

    private static UnsupportedOperationException unused() {
        return new UnsupportedOperationException("not part of the write-axis fake");
    }

    @Override public Optional<OperatorView> findByOidcSubject(String s) { throw unused(); }
    @Override public boolean existsByTenantIdAndEmail(String t, String e) { throw unused(); }
    @Override public boolean existsMemberWithEmail(String t, String e, Long x) { throw unused(); }
    @Override public OperatorPage findOperatorsPage(String s, int p, int z) { throw unused(); }
    @Override public OperatorPage findOperatorsPageByTenant(String t, String s, int p, int z) { throw unused(); }
    @Override public List<OperatorLookupView> findLookupCandidates(String t, String e) { throw unused(); }
    @Override public OperatorView createOperator(NewOperator row) { throw unused(); }
    @Override public List<OperatorOidcSubjectView> findOperatorsWithOidcSubject() { throw unused(); }
    @Override public boolean existsOperatorBySubject(String a) { throw unused(); }
    @Override public boolean existsOperatorByIdentity(String i) { throw unused(); }
    @Override public void updateOidcSubject(long id, String s, Instant at) { throw unused(); }
    @Override public void changeStatus(long id, String s, Instant at) { throw unused(); }
    @Override public void changePasswordHash(long id, String h, Instant at) { throw unused(); }
    @Override public void changeFinanceDefaultAccountId(long id, String v, Instant at) { throw unused(); }
    @Override public void linkIdentity(long id, String i, Instant at) { throw unused(); }
    @Override public void unlinkIdentity(long id, Instant at) { throw unused(); }
    @Override public List<RoleView> findRolesForOperator(long id) { throw unused(); }
    @Override public List<RoleView> findAllRoles() { throw unused(); }
    @Override public boolean anyRoleRequires2fa(long id) { throw unused(); }
    @Override public Map<Long, List<String>> bulkLoadRoleNamesByOperator(Collection<Long> ids) { throw unused(); }
    @Override public Map<Long, List<String>> bulkLoadRoleNamesByOperatorInTenant(Collection<Long> ids, String t) {
        throw unused();
    }
}
