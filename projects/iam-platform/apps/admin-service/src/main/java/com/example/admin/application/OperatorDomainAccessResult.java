package com.example.admin.application;

import com.example.admin.domain.rbac.DomainRoleCatalog.RoleRef;

import java.time.Instant;
import java.util.List;

/**
 * TASK-BE-631 — an operator's effective work roles in one tenant plus, per set, where each grant came from
 * (admin-api.md § GET …/operators/{operatorId}/domain-access · § GET /me/domain-access). {@code roles} is the union
 * of the sets' roles BEFORE any catalog / subscription intersection (the token's value is auth-service's).
 */
public record OperatorDomainAccessResult(String operatorId, String tenantId, List<RoleRef> roles,
                                         List<SetAccess> sets) {

    /** One set the operator holds there, with every source row that grants it. */
    public record SetAccess(String setId, String name, String systemKey, List<Source> sources) {}

    /**
     * One grant row: {@code source} = {@code DIRECT} · {@code MIGRATION} · {@code GROUP} (then {@code groupId} and
     * {@code groupName}); {@code tenantId} = the row's tenant — T, or {@code '*'} for a {@code '*'} operator's
     * all-tenants row; {@code grantedBy} = the granting operator id, {@code null} for MIGRATION.
     */
    public record Source(String source, String groupId, String groupName, String tenantId, Instant grantedAt,
                         String grantedBy) {}

    static OperatorDomainAccessResult empty(String operatorId, String tenantId) {
        return new OperatorDomainAccessResult(operatorId, tenantId, List.of(), List.of());
    }
}
