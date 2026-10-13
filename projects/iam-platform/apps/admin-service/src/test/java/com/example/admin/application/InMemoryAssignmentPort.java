package com.example.admin.application;

import com.example.admin.application.port.OperatorTenantAssignmentPort;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * TASK-MONO-784 S4 — an in-memory {@code operator_tenant_assignment} for the export unit tests (owner decision
 * 2026-10-13 UTC «U 소속을 직접 소속으로 남김»): a mock cannot show that a row SURVIVED as a direct membership with
 * its {@code granted_at} / {@code org_scope} unchanged; this fake really converts and really deletes.
 */
final class InMemoryAssignmentPort implements OperatorTenantAssignmentPort {

    record Row(long operatorId, String tenantId, Instant grantedAt, Long grantedBy, List<String> orgScope,
               Long groupOrigin) {}

    final List<Row> rows = new ArrayList<>();

    InMemoryAssignmentPort row(long operatorId, String tenantId, Long groupOrigin, List<String> orgScope) {
        rows.add(new Row(operatorId, tenantId, Instant.parse("2026-02-02T00:00:00Z"), 5L, orgScope, groupOrigin));
        return this;
    }

    Optional<Row> find(long operatorId, String tenantId) {
        return rows.stream().filter(r -> r.operatorId() == operatorId && r.tenantId().equals(tenantId)).findFirst();
    }

    @Override
    public Set<String> findAssignedTenantIds(Long operatorInternalId) {
        Set<String> out = new LinkedHashSet<>();
        rows.stream().filter(r -> r.operatorId() == operatorInternalId).forEach(r -> out.add(r.tenantId()));
        return out;
    }

    @Override
    public boolean assignmentExists(Long operatorInternalId, String tenantId) {
        return find(operatorInternalId, tenantId).isPresent();
    }

    @Override
    public void deleteAssignment(Long operatorInternalId, String tenantId) {
        rows.removeIf(r -> r.operatorId() == operatorInternalId && r.tenantId().equals(tenantId));
    }

    @Override
    public int deleteGroupOriginAssignments(Long operatorInternalId, Long groupInternalId) {
        int before = rows.size();
        rows.removeIf(r -> r.operatorId() == operatorInternalId && groupInternalId.equals(r.groupOrigin()));
        return before - rows.size();
    }

    @Override
    public List<String> findGroupOriginAssignmentTenants(Long operatorInternalId, Long groupInternalId) {
        return rows.stream()
                .filter(r -> r.operatorId() == operatorInternalId && groupInternalId.equals(r.groupOrigin()))
                .map(Row::tenantId).toList();
    }

    @Override
    public void convertToDirectAssignment(Long operatorInternalId, String tenantId) {
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            if (r.operatorId() == operatorInternalId && r.tenantId().equals(tenantId)) {
                rows.set(i, new Row(r.operatorId(), r.tenantId(), r.grantedAt(), r.grantedBy(), r.orgScope(), null));
            }
        }
    }

    // ---------- not used by the export ----------

    @Override public List<String> findOrgScope(Long op, String t) { throw new UnsupportedOperationException(); }
    @Override public Optional<AssignmentView> findAssignment(Long op, String t) { throw new UnsupportedOperationException(); }
    @Override public void updateOrgScope(Long op, String t, List<String> s) { throw new UnsupportedOperationException(); }
    @Override public void createAssignment(Long op, String t, Long by) { throw new UnsupportedOperationException(); }
}
