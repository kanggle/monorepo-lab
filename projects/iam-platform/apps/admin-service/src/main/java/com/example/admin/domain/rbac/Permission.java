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

    /** Sentinel recorded when a controller method is missing a permission declaration. */
    public static final String MISSING = "<missing>";
}
