package com.example.admin.domain.rbac;

import java.util.List;

/**
 * Canonical permission key catalog for admin-service RBAC.
 * See specs/services/admin-service/rbac.md — "Permission Keys".
 *
 * Values are the exact strings persisted in admin_role_permissions.permission_key
 * and echoed into admin_actions.permission_used. Do not introduce new keys
 * without updating rbac.md.
 */
public final class Permission {

    private Permission() {}

    public static final String ACCOUNT_READ           = "account.read";
    public static final String ACCOUNT_LOCK          = "account.lock";
    public static final String ACCOUNT_UNLOCK        = "account.unlock";
    public static final String ACCOUNT_FORCE_LOGOUT  = "account.force_logout";
    public static final String AUDIT_READ            = "audit.read";
    public static final String SECURITY_EVENT_READ   = "security.event.read";
    public static final String OPERATOR_MANAGE       = "operator.manage";
    /** TASK-BE-250: tenant lifecycle management (SUPER_ADMIN only). */
    public static final String TENANT_MANAGE         = "tenant.manage";
    /**
     * TASK-BE-343 (ADR-MONO-023 D3): tenant↔domain subscription lifecycle
     * management (SUPER_ADMIN only). DISTINCT from {@link #OPERATOR_MANAGE} — the
     * entitlement plane and the IAM (operator) plane are separately delegable
     * (ADR-023 D2/D3). The operator-facing subscription surface delegates the
     * write to account-service (the entitlement authority).
     */
    public static final String SUBSCRIPTION_MANAGE   = "subscription.manage";
    /**
     * TASK-BE-346 (ADR-MONO-024 D4-B): in-tenant sub-delegation authority. Held by
     * the {@code TENANT_ADMIN} role only — a tenant-admin carrying this permission
     * may appoint further {@code TENANT_ADMIN}s WITHIN ITS OWN TENANT (the D2
     * confinement makes cross-tenant structurally impossible). NOT held by
     * SUPER_ADMIN (which delegates via its platform-unconstrained authority, not
     * via this key). The grant-menu admission rule lands in step 2b.
     */
    public static final String TENANT_ADMIN_DELEGATE = "tenant.admin.delegate";
    /**
     * TASK-BE-477 (ADR-MONO-045 D2/D4): cross-org partnership management authority.
     * Held by the {@code TENANT_ADMIN} role only — a tenant-admin carrying this
     * permission may (a) as host, invite/terminate a partner; (b) as partner, accept;
     * (c) as partner, assign/remove its own operators as participants — all confined
     * by the D2 {@code TenantScopeGuard} to partnerships where the acting-side tenant
     * is a party. NOT held by SUPER_ADMIN (a partnership is a relationship between two
     * real customer tenants; the platform is not a party — the D2-C broker gate is
     * deferred, mirroring {@code tenant.admin.delegate} ❌ on SUPER_ADMIN). This key
     * gates only the partnership MANAGEMENT surface; the cross-org DERIVED
     * domain-operating authority a partner operator gains in the host is capped by a
     * separate axis ({@code delegated_scope ∩ participant ∩ host-holds}) and never
     * widens admin scope.
     */
    public static final String PARTNERSHIP_MANAGE = "partnership.manage";
    /**
     * TASK-BE-492 (ADR-MONO-047 D5): org-node tree authority — node CRUD, ceiling edits,
     * and {@code ORG_ADMIN} grant/revoke. Held by {@code SUPER_ADMIN} (the only principal
     * that may create a ROOT node) and by the new {@code ORG_ADMIN} role, whose grant is
     * scoped to an org-node rather than to a tenant.
     *
     * <p>An org-node is a <b>data-less grouping node above {@code tenant}</b> — it groups
     * tenants, it never nests them (ADR-047 D1), so the M1 single flat isolation key is
     * untouched. admin-service does not store the tree: this key gates a <b>thin command
     * gateway</b> onto account-service, which owns {@code tenants} and therefore
     * {@code org_node} (D6).
     *
     * <p>Holding this key does NOT widen admin scope by itself — an {@code ORG_ADMIN}'s
     * reach is the tenant subtree under the node named by its grant row's
     * {@code admin_operator_roles.org_node_id}, resolved fail-closed.
     */
    public static final String ORG_MANAGE = "org.manage";

    /** Sentinel recorded when a controller method is missing a permission declaration. */
    public static final String MISSING = "<missing>";

    /**
     * TASK-BE-486 — the full, ordered permission-key catalog exposed by
     * {@code GET /api/admin/permissions}. This is the canonical enumeration of
     * every real permission key (the {@link #MISSING} sentinel is deliberately
     * excluded — it is an audit marker, not a grantable permission), in the order
     * declared by rbac.md § Permission Keys.
     *
     * <p>Sourcing the catalog from these constants (rather than {@code SELECT
     * DISTINCT permission_key FROM admin_role_permissions}) makes the response
     * drift-proof against seed state: a key that is defined but not yet granted
     * to any seed role still appears. {@code CatalogDriftTest} pins this list to
     * the reflected constant set so a new constant can never silently omit itself.
     */
    private static final List<String> CATALOG = List.of(
            ACCOUNT_READ,
            ACCOUNT_LOCK,
            ACCOUNT_UNLOCK,
            ACCOUNT_FORCE_LOGOUT,
            AUDIT_READ,
            SECURITY_EVENT_READ,
            OPERATOR_MANAGE,
            TENANT_MANAGE,
            SUBSCRIPTION_MANAGE,
            TENANT_ADMIN_DELEGATE,
            PARTNERSHIP_MANAGE,
            ORG_MANAGE);

    /** @return the canonical permission-key catalog (immutable, rbac.md order). */
    public static List<String> catalog() {
        return CATALOG;
    }
}
