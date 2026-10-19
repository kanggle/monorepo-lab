package com.example.erp.masterdata.domain.authorization;

import java.util.Set;

/**
 * The data scope a scoped LIST use case may read (TASK-ERP-BE-045 — {@code GET /employees/picker}), resolved by
 * the authorization port from the actor's {@code org_scope} AFTER the role check passed.
 *
 * <ul>
 *   <li>{@link Kind#TENANT} — {@code org_scope ∋ "*"}: the whole tenant; {@link #departmentIds()} is empty and
 *       not consulted.</li>
 *   <li>{@link Kind#SUBTREE} — the departments under the actor's subtree roots, already EXPANDED (roots ∪ every
 *       descendant). Empty = the actor has no data scope → the list is empty (fail-closed, not 403: the role
 *       already passed).</li>
 * </ul>
 *
 * <p>Pure Java — no framework imports.
 */
public record ListDataScope(Kind kind, Set<String> departmentIds) {

    public enum Kind { TENANT, SUBTREE }

    public ListDataScope {
        if (kind == null) {
            throw new IllegalArgumentException("kind");
        }
        departmentIds = departmentIds == null ? Set.of() : Set.copyOf(departmentIds);
    }

    public static ListDataScope tenant() {
        return new ListDataScope(Kind.TENANT, Set.of());
    }

    public static ListDataScope subtree(Set<String> expandedDepartmentIds) {
        return new ListDataScope(Kind.SUBTREE, expandedDepartmentIds);
    }

    public static ListDataScope none() {
        return new ListDataScope(Kind.SUBTREE, Set.of());
    }

    public boolean isTenantWide() {
        return kind == Kind.TENANT;
    }

    /** {@code true} iff this scope admits no row at all (SUBTREE with no department). */
    public boolean isEmpty() {
        return kind == Kind.SUBTREE && departmentIds.isEmpty();
    }
}
