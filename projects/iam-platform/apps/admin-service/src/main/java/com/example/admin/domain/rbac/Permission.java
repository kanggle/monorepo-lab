package com.example.admin.domain.rbac;

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

    /** Sentinel recorded when a controller method is missing a permission declaration. */
    public static final String MISSING = "<missing>";
}
