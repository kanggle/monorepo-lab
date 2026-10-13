package com.example.admin.application.port;

import com.example.admin.domain.rbac.DomainRoleCatalog.RoleRef;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * TASK-BE-631 (ADR-MONO-083) — port over {@code domain_permission_set} · {@code domain_permission_set_role} ·
 * {@code operator_domain_set_grant} · {@code operator_group_domain_set_grant} (V0052). Keeps the JPA types out of
 * the application layer (the {@link OperatorTenantAssignmentPort} pattern).
 *
 * <p>Operators and groups are addressed by their internal BIGINT ids; sets by the internal id once loaded
 * ({@link SetView#internalId()}), by the external {@code setId} only on lookup.
 */
public interface DomainPermissionSetPort {

    // ---------- Sets ----------

    /** One set (preset or tenant set) by its external id, with its roles. */
    Optional<SetView> findBySetId(String setId);

    /** Every system preset ({@code tenant_id IS NULL}), with roles. Order: implementation-defined. */
    List<SetView> findPresets();

    /** The tenant sets of {@code tenantId}, with roles, {@code created_at} ascending. */
    List<SetView> findTenantSets(String tenantId);

    /** Inserts a tenant set and its roles; flushes. */
    SetView createTenantSet(String setId, String tenantId, String name, String description, List<RoleRef> roles,
                            Long createdByInternalId, Instant now);

    /**
     * Edits a tenant set: a {@code null} argument leaves that field unchanged; non-null {@code roles} REPLACES the
     * role rows. Bumps {@code updated_at} and the optimistic-lock version (a concurrent edit → 409
     * {@code OPTIMISTIC_LOCK_CONFLICT}).
     */
    SetView updateTenantSet(long setInternalId, String name, String description, List<RoleRef> roles, Instant now);

    /** Deletes a set and its role rows. The caller has checked {@link #countAllGrantRows} / {@link #countAllGroupGrants}. */
    void deleteSet(long setInternalId);

    /** Operator grant rows pointing at the set in {@code tenantId}, every source (wire {@code grantCount}). */
    long countGrantRows(long setInternalId, String tenantId);

    /** Groups of {@code tenantId} the set is granted to (wire {@code groupCount}). */
    long countGroupGrants(long setInternalId, String tenantId);

    /** Operator grant rows pointing at the set anywhere ({@code 409 DOMAIN_PERMISSION_SET_IN_USE}). */
    long countAllGrantRows(long setInternalId);

    /** Group grants of the set anywhere ({@code 409 DOMAIN_PERMISSION_SET_IN_USE}). */
    long countAllGroupGrants(long setInternalId);

    // ---------- Operator grants ----------

    /** Every grant row of one operator whose {@code tenant_id} is in {@code tenantIds}, with its set's identity. */
    List<GrantRowView> findGrantRows(long operatorInternalId, Collection<String> tenantIds);

    /** Whether the exact row (operator, tenant, set, source) exists. */
    boolean grantExists(long operatorInternalId, String tenantId, long setInternalId, String sourceKey);

    /**
     * Inserts one grant row. {@code groupOrigin} must be the group id exactly when {@code sourceKey} is
     * {@code GROUP:<id>} (the invariant the schema cannot CHECK — V0052 header).
     */
    void insertGrant(long operatorInternalId, String tenantId, long setInternalId, String sourceKey,
                     Long groupOrigin, Long grantedByInternalId, Instant grantedAt);

    /**
     * Deletes the rows of (operator, tenant, set) whose source is in {@code sourceKeys}; other sources' rows are
     * untouched.
     *
     * @return the source keys of the rows actually deleted
     */
    List<String> deleteGrants(long operatorInternalId, String tenantId, long setInternalId,
                              Collection<String> sourceKeys);

    // ---------- Projections ----------

    /**
     * A set with its roles (catalog order, distinct). {@code tenantId == null} ⟺ {@code systemKey != null} ⟺ preset.
     */
    record SetView(long internalId, String setId, String tenantId, String systemKey, String name,
                   String description, List<RoleRef> roles, Instant createdAt, Instant updatedAt) {
        public boolean isPreset() {
            return tenantId == null;
        }
    }

    /**
     * One {@code operator_domain_set_grant} row, joined with what the wire needs: the set's identity and roles, the
     * granting operator's external id ({@code null} for MIGRATION / a deleted granter), and for a GROUP row the
     * group's external id and name.
     */
    record GrantRowView(long operatorInternalId, String tenantId, SetView set, String sourceKey,
                        Long groupOrigin, String groupId, String groupName,
                        String grantedByOperatorId, Instant grantedAt) {}
}
