package com.example.admin.application;

import com.example.admin.domain.rbac.Permission;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Static catalog of the (ActionCode → audit target.type) mapping and the
 * (ActionCode → canonical Permission key) resolution used by the admin audit
 * subsystem.
 *
 * <p>Extracted from {@link AdminActionAuditor} (TASK-BE-314) so the
 * permission/target catalog can evolve independently of the audit write path.
 * Behavior is byte-equal to the pre-split logic — every mapping entry, every
 * switch arm, and the {@code normalizeTargetType} legacy fixups are preserved
 * verbatim.
 *
 * <p>Synthetic permission strings ({@code <self_action>}, {@code auth.2fa_*},
 * {@code auth.login}, {@code auth.refresh}, {@code auth.logout}) intentionally
 * live on {@link AdminActionAuditor} so that external callers (controllers /
 * aspects) preserve their byte-for-byte import shape; this registry consumes
 * those constants from the facade.
 */
@Component
public class AdminActionPermissionRegistry {

    /** Canonical actionCode → target.type mapping used for DENIED rows. */
    private static final Map<ActionCode, String> ACTION_TARGET_TYPE;
    static {
        var map = new HashMap<ActionCode, String>();
        map.put(ActionCode.ACCOUNT_LOCK, "ACCOUNT");
        map.put(ActionCode.ACCOUNT_UNLOCK, "ACCOUNT");
        map.put(ActionCode.SESSION_REVOKE, "SESSION");
        map.put(ActionCode.AUDIT_QUERY, "AUDIT_QUERY");
        // TASK-BE-357 — account search/list (DENIED cross-tenant row target type)
        map.put(ActionCode.ACCOUNT_SEARCH, "ACCOUNT");
        // TASK-BE-029-2 — self-directed 2FA enroll/verify
        map.put(ActionCode.OPERATOR_2FA_ENROLL, "OPERATOR");
        map.put(ActionCode.OPERATOR_2FA_VERIFY, "OPERATOR");
        // TASK-BE-113 — self-directed recovery-code regeneration
        map.put(ActionCode.OPERATOR_2FA_RECOVERY_REGENERATE, "OPERATOR");
        // TASK-BE-029-3 — login audit rows
        map.put(ActionCode.OPERATOR_LOGIN, "OPERATOR");
        // TASK-BE-040 — refresh rotation + self-logout
        map.put(ActionCode.OPERATOR_REFRESH, "OPERATOR");
        map.put(ActionCode.OPERATOR_LOGOUT, "OPERATOR");
        // TASK-BE-054 — GDPR/PIPA data rights
        map.put(ActionCode.GDPR_DELETE, "ACCOUNT");
        map.put(ActionCode.DATA_EXPORT, "ACCOUNT");
        // TASK-BE-083 — operator management mutations
        map.put(ActionCode.OPERATOR_CREATE, "OPERATOR");
        map.put(ActionCode.OPERATOR_ROLE_CHANGE, "OPERATOR");
        map.put(ActionCode.OPERATOR_STATUS_CHANGE, "OPERATOR");
        // TASK-BE-250 — tenant lifecycle management
        map.put(ActionCode.TENANT_CREATE, "TENANT");
        map.put(ActionCode.TENANT_SUSPEND, "TENANT");
        map.put(ActionCode.TENANT_REACTIVATE, "TENANT");
        map.put(ActionCode.TENANT_UPDATE, "TENANT");
        // TASK-BE-306 — self-serve operator profile mutation
        map.put(ActionCode.OPERATOR_PROFILE_UPDATE, "OPERATOR");
        // TASK-BE-339 — per-assignment org_scope set/clear
        map.put(ActionCode.OPERATOR_ORG_SCOPE_UPDATE, "OPERATOR");
        // TASK-BE-343 (ADR-MONO-023 D3) — subscription lifecycle management
        map.put(ActionCode.SUBSCRIPTION_SUBSCRIBE, "SUBSCRIPTION");
        map.put(ActionCode.SUBSCRIPTION_CHANGE_STATUS, "SUBSCRIPTION");
        // TASK-BE-347 (ADR-MONO-024 D3-i) — operator↔tenant assignment create/remove
        map.put(ActionCode.OPERATOR_ASSIGNMENT_CREATE, "OPERATOR");
        map.put(ActionCode.OPERATOR_ASSIGNMENT_DELETE, "OPERATOR");
        ACTION_TARGET_TYPE = Map.copyOf(map);
    }

    /** @return target.type for the given action code; {@code "UNKNOWN"} when unmapped or null. */
    public String targetTypeFor(ActionCode code) {
        if (code == null) return "UNKNOWN";
        return ACTION_TARGET_TYPE.getOrDefault(code, "UNKNOWN");
    }

    /**
     * Upper-cases legacy lowercase target_type passed by existing use-cases and
     * remaps historical aliases to the canonical envelope target.type. Behavior
     * preserved verbatim from the pre-split {@code AdminActionAuditor}.
     */
    public String normalizeTargetType(String raw, ActionCode code) {
        if (raw == null || raw.isBlank()) return targetTypeFor(code);
        String upper = raw.toUpperCase(Locale.ROOT);
        // historical use-cases passed "account"/"audit" — remap to envelope spec
        if ("ACCOUNT".equals(upper) && code == ActionCode.SESSION_REVOKE) return "SESSION";
        if ("AUDIT".equals(upper)) return "AUDIT_QUERY";
        return upper;
    }

    /**
     * Resolves the canonical {@code permission_used} string stamped on the
     * audit row + outbox envelope for the given action code. Returns
     * {@link Permission#MISSING} when the code is null.
     *
     * <p>Self-flow action codes resolve to the synthetic permission constants
     * declared on {@link AdminActionAuditor} (kept on the facade for callsite
     * import stability — see class javadoc).
     */
    public String permissionForActionCode(ActionCode code) {
        if (code == null) return Permission.MISSING;
        return switch (code) {
            case ACCOUNT_LOCK -> Permission.ACCOUNT_LOCK;
            case ACCOUNT_UNLOCK -> Permission.ACCOUNT_UNLOCK;
            case SESSION_REVOKE -> Permission.ACCOUNT_FORCE_LOGOUT;
            case AUDIT_QUERY -> Permission.AUDIT_READ;
            // TASK-BE-357 — account search/list reads (DENIED cross-tenant row permission_used)
            case ACCOUNT_SEARCH -> Permission.ACCOUNT_READ;
            // 029-2: synthetic permission strings for the unauthenticated
            // 2FA sub-tree (no grantable permission; treat as sentinel for audit).
            case OPERATOR_2FA_ENROLL -> AdminActionAuditor.PERMISSION_2FA_ENROLL;
            case OPERATOR_2FA_VERIFY -> AdminActionAuditor.PERMISSION_2FA_VERIFY;
            case OPERATOR_2FA_RECOVERY_REGENERATE -> AdminActionAuditor.PERMISSION_2FA_RECOVERY_REGENERATE;
            case OPERATOR_LOGIN -> AdminActionAuditor.PERMISSION_LOGIN;
            case OPERATOR_REFRESH -> AdminActionAuditor.PERMISSION_REFRESH;
            case OPERATOR_LOGOUT -> AdminActionAuditor.PERMISSION_LOGOUT;
            case GDPR_DELETE -> Permission.ACCOUNT_LOCK;
            case DATA_EXPORT -> Permission.AUDIT_READ;
            // TASK-BE-083 — all operator management mutations gate on the same permission key.
            // TASK-BE-339 — org_scope set/clear is an operator management mutation too.
            case OPERATOR_CREATE, OPERATOR_ROLE_CHANGE, OPERATOR_STATUS_CHANGE,
                 OPERATOR_ORG_SCOPE_UPDATE,
                 // TASK-BE-347 (ADR-MONO-024 D3-i) — assignment create/remove gate on operator.manage
                 OPERATOR_ASSIGNMENT_CREATE, OPERATOR_ASSIGNMENT_DELETE -> Permission.OPERATOR_MANAGE;
            // TASK-BE-250 — tenant lifecycle management
            case TENANT_CREATE, TENANT_SUSPEND, TENANT_REACTIVATE, TENANT_UPDATE -> Permission.TENANT_MANAGE;
            // TASK-BE-343 (ADR-MONO-023 D3) — subscription lifecycle management
            case SUBSCRIPTION_SUBSCRIBE, SUBSCRIPTION_CHANGE_STATUS -> Permission.SUBSCRIPTION_MANAGE;
            // TASK-BE-306 — self-serve operator profile mutation (no grantable permission;
            // synthetic <self_action> sentinel for symmetry with reason="<self_profile_update>").
            case OPERATOR_PROFILE_UPDATE -> AdminActionAuditor.PERMISSION_SELF_ACTION;
        };
    }
}
