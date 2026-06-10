package com.example.admin.application;

import com.example.admin.domain.rbac.Permission;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Locks down the audit subsystem's permission / target-type catalog so that the
 * TASK-BE-314 extraction cannot drift from the byte-equal pre-split values.
 *
 * <p>Two byte-equality guarantees are enforced here:
 * <ol>
 *   <li>The {@code REASON_*} / {@code PERMISSION_*} synthetic constants on
 *       {@link AdminActionAuditor} are pinned to their literal strings — these
 *       are persisted in {@code admin_actions.reason} / {@code permission_used}
 *       and matched verbatim by audit consumers.</li>
 *   <li>The (ActionCode → target.type) and (ActionCode → permission key)
 *       mappings on {@link AdminActionPermissionRegistry} produce the same
 *       values the pre-split {@code AdminActionAuditor} produced.</li>
 * </ol>
 */
class AdminActionPermissionRegistryTest {

    private final AdminActionPermissionRegistry registry = new AdminActionPermissionRegistry();

    // ── Constant byte-equality (facade-resident, used by callers) ────────────

    @Test
    void reason_constants_are_byte_for_byte_preserved() {
        assertThat(AdminActionAuditor.REASON_SELF_ENROLLMENT).isEqualTo("<self_enrollment>");
        assertThat(AdminActionAuditor.REASON_SELF_LOGIN).isEqualTo("<self_login>");
        assertThat(AdminActionAuditor.REASON_SELF_REFRESH).isEqualTo("<self_refresh>");
        assertThat(AdminActionAuditor.REASON_SELF_LOGOUT).isEqualTo("<self_logout>");
        assertThat(AdminActionAuditor.REASON_SELF_RECOVERY_REGENERATE).isEqualTo("<self_recovery_regenerate>");
        assertThat(AdminActionAuditor.REASON_SELF_PROFILE_UPDATE).isEqualTo("<self_profile_update>");
    }

    @Test
    void permission_constants_are_byte_for_byte_preserved() {
        assertThat(AdminActionAuditor.PERMISSION_2FA_ENROLL).isEqualTo("auth.2fa_enroll");
        assertThat(AdminActionAuditor.PERMISSION_2FA_VERIFY).isEqualTo("auth.2fa_verify");
        assertThat(AdminActionAuditor.PERMISSION_2FA_RECOVERY_REGENERATE).isEqualTo("auth.2fa_recovery_regenerate");
        assertThat(AdminActionAuditor.PERMISSION_LOGIN).isEqualTo("auth.login");
        assertThat(AdminActionAuditor.PERMISSION_REFRESH).isEqualTo("auth.refresh");
        assertThat(AdminActionAuditor.PERMISSION_LOGOUT).isEqualTo("auth.logout");
        assertThat(AdminActionAuditor.PERMISSION_SELF_ACTION).isEqualTo("<self_action>");
    }

    // ── targetTypeFor(ActionCode) ────────────────────────────────────────────

    @Test
    void targetTypeFor_account_action_codes() {
        assertThat(registry.targetTypeFor(ActionCode.ACCOUNT_LOCK)).isEqualTo("ACCOUNT");
        assertThat(registry.targetTypeFor(ActionCode.ACCOUNT_UNLOCK)).isEqualTo("ACCOUNT");
        assertThat(registry.targetTypeFor(ActionCode.GDPR_DELETE)).isEqualTo("ACCOUNT");
        assertThat(registry.targetTypeFor(ActionCode.DATA_EXPORT)).isEqualTo("ACCOUNT");
    }

    @Test
    void targetTypeFor_operator_action_codes() {
        assertThat(registry.targetTypeFor(ActionCode.OPERATOR_2FA_ENROLL)).isEqualTo("OPERATOR");
        assertThat(registry.targetTypeFor(ActionCode.OPERATOR_2FA_VERIFY)).isEqualTo("OPERATOR");
        assertThat(registry.targetTypeFor(ActionCode.OPERATOR_2FA_RECOVERY_REGENERATE)).isEqualTo("OPERATOR");
        assertThat(registry.targetTypeFor(ActionCode.OPERATOR_LOGIN)).isEqualTo("OPERATOR");
        assertThat(registry.targetTypeFor(ActionCode.OPERATOR_REFRESH)).isEqualTo("OPERATOR");
        assertThat(registry.targetTypeFor(ActionCode.OPERATOR_LOGOUT)).isEqualTo("OPERATOR");
        assertThat(registry.targetTypeFor(ActionCode.OPERATOR_CREATE)).isEqualTo("OPERATOR");
        assertThat(registry.targetTypeFor(ActionCode.OPERATOR_ROLE_CHANGE)).isEqualTo("OPERATOR");
        assertThat(registry.targetTypeFor(ActionCode.OPERATOR_STATUS_CHANGE)).isEqualTo("OPERATOR");
        assertThat(registry.targetTypeFor(ActionCode.OPERATOR_PROFILE_UPDATE)).isEqualTo("OPERATOR");
    }

    @Test
    void targetTypeFor_session_and_audit_query() {
        assertThat(registry.targetTypeFor(ActionCode.SESSION_REVOKE)).isEqualTo("SESSION");
        assertThat(registry.targetTypeFor(ActionCode.AUDIT_QUERY)).isEqualTo("AUDIT_QUERY");
    }

    @Test
    void targetTypeFor_tenant_action_codes() {
        assertThat(registry.targetTypeFor(ActionCode.TENANT_CREATE)).isEqualTo("TENANT");
        assertThat(registry.targetTypeFor(ActionCode.TENANT_SUSPEND)).isEqualTo("TENANT");
        assertThat(registry.targetTypeFor(ActionCode.TENANT_REACTIVATE)).isEqualTo("TENANT");
        assertThat(registry.targetTypeFor(ActionCode.TENANT_UPDATE)).isEqualTo("TENANT");
    }

    @Test
    void targetTypeFor_subscription_action_codes() {
        assertThat(registry.targetTypeFor(ActionCode.SUBSCRIPTION_SUBSCRIBE)).isEqualTo("SUBSCRIPTION");
        assertThat(registry.targetTypeFor(ActionCode.SUBSCRIPTION_CHANGE_STATUS)).isEqualTo("SUBSCRIPTION");
    }

    @Test
    void targetTypeFor_null_returns_unknown_sentinel() {
        assertThat(registry.targetTypeFor(null)).isEqualTo("UNKNOWN");
    }

    @Test
    void targetTypeFor_covers_every_action_code() {
        // Guards against a future ActionCode being added without a mapping
        // entry — the resolver must never return "UNKNOWN" for a real code.
        for (ActionCode code : ActionCode.values()) {
            assertThat(registry.targetTypeFor(code))
                    .as("targetTypeFor(%s)", code)
                    .isNotEqualTo("UNKNOWN");
        }
    }

    // ── normalizeTargetType(raw, code) ───────────────────────────────────────

    @Test
    void normalizeTargetType_blank_falls_back_to_action_code_target() {
        assertThat(registry.normalizeTargetType(null, ActionCode.ACCOUNT_LOCK)).isEqualTo("ACCOUNT");
        assertThat(registry.normalizeTargetType("", ActionCode.ACCOUNT_LOCK)).isEqualTo("ACCOUNT");
        assertThat(registry.normalizeTargetType("   ", ActionCode.ACCOUNT_LOCK)).isEqualTo("ACCOUNT");
    }

    @Test
    void normalizeTargetType_uppercases_lowercase_raw() {
        assertThat(registry.normalizeTargetType("operator", ActionCode.OPERATOR_LOGIN))
                .isEqualTo("OPERATOR");
    }

    @Test
    void normalizeTargetType_remaps_legacy_account_for_session_revoke_to_session() {
        assertThat(registry.normalizeTargetType("ACCOUNT", ActionCode.SESSION_REVOKE))
                .isEqualTo("SESSION");
        assertThat(registry.normalizeTargetType("account", ActionCode.SESSION_REVOKE))
                .isEqualTo("SESSION");
    }

    @Test
    void normalizeTargetType_remaps_legacy_audit_to_audit_query() {
        assertThat(registry.normalizeTargetType("AUDIT", ActionCode.AUDIT_QUERY))
                .isEqualTo("AUDIT_QUERY");
        assertThat(registry.normalizeTargetType("audit", ActionCode.AUDIT_QUERY))
                .isEqualTo("AUDIT_QUERY");
    }

    // ── permissionForActionCode(ActionCode) ──────────────────────────────────

    @Test
    void permissionForActionCode_null_returns_missing_sentinel() {
        assertThat(registry.permissionForActionCode(null)).isEqualTo(Permission.MISSING);
    }

    @Test
    void permissionForActionCode_account_lifecycle() {
        assertThat(registry.permissionForActionCode(ActionCode.ACCOUNT_LOCK))
                .isEqualTo(Permission.ACCOUNT_LOCK);
        assertThat(registry.permissionForActionCode(ActionCode.ACCOUNT_UNLOCK))
                .isEqualTo(Permission.ACCOUNT_UNLOCK);
        assertThat(registry.permissionForActionCode(ActionCode.SESSION_REVOKE))
                .isEqualTo(Permission.ACCOUNT_FORCE_LOGOUT);
        assertThat(registry.permissionForActionCode(ActionCode.AUDIT_QUERY))
                .isEqualTo(Permission.AUDIT_READ);
    }

    @Test
    void permissionForActionCode_self_flow_synthetic_keys() {
        assertThat(registry.permissionForActionCode(ActionCode.OPERATOR_2FA_ENROLL))
                .isEqualTo(AdminActionAuditor.PERMISSION_2FA_ENROLL);
        assertThat(registry.permissionForActionCode(ActionCode.OPERATOR_2FA_VERIFY))
                .isEqualTo(AdminActionAuditor.PERMISSION_2FA_VERIFY);
        assertThat(registry.permissionForActionCode(ActionCode.OPERATOR_2FA_RECOVERY_REGENERATE))
                .isEqualTo(AdminActionAuditor.PERMISSION_2FA_RECOVERY_REGENERATE);
        assertThat(registry.permissionForActionCode(ActionCode.OPERATOR_LOGIN))
                .isEqualTo(AdminActionAuditor.PERMISSION_LOGIN);
        assertThat(registry.permissionForActionCode(ActionCode.OPERATOR_REFRESH))
                .isEqualTo(AdminActionAuditor.PERMISSION_REFRESH);
        assertThat(registry.permissionForActionCode(ActionCode.OPERATOR_LOGOUT))
                .isEqualTo(AdminActionAuditor.PERMISSION_LOGOUT);
        assertThat(registry.permissionForActionCode(ActionCode.OPERATOR_PROFILE_UPDATE))
                .isEqualTo(AdminActionAuditor.PERMISSION_SELF_ACTION);
    }

    @Test
    void permissionForActionCode_gdpr_paths_alias_to_existing_keys() {
        assertThat(registry.permissionForActionCode(ActionCode.GDPR_DELETE))
                .isEqualTo(Permission.ACCOUNT_LOCK);
        assertThat(registry.permissionForActionCode(ActionCode.DATA_EXPORT))
                .isEqualTo(Permission.AUDIT_READ);
    }

    @Test
    void permissionForActionCode_operator_management_share_operator_manage() {
        assertThat(registry.permissionForActionCode(ActionCode.OPERATOR_CREATE))
                .isEqualTo(Permission.OPERATOR_MANAGE);
        assertThat(registry.permissionForActionCode(ActionCode.OPERATOR_ROLE_CHANGE))
                .isEqualTo(Permission.OPERATOR_MANAGE);
        assertThat(registry.permissionForActionCode(ActionCode.OPERATOR_STATUS_CHANGE))
                .isEqualTo(Permission.OPERATOR_MANAGE);
    }

    @Test
    void permissionForActionCode_tenant_lifecycle_share_tenant_manage() {
        assertThat(registry.permissionForActionCode(ActionCode.TENANT_CREATE))
                .isEqualTo(Permission.TENANT_MANAGE);
        assertThat(registry.permissionForActionCode(ActionCode.TENANT_SUSPEND))
                .isEqualTo(Permission.TENANT_MANAGE);
        assertThat(registry.permissionForActionCode(ActionCode.TENANT_REACTIVATE))
                .isEqualTo(Permission.TENANT_MANAGE);
        assertThat(registry.permissionForActionCode(ActionCode.TENANT_UPDATE))
                .isEqualTo(Permission.TENANT_MANAGE);
    }

    @Test
    void permissionForActionCode_subscription_lifecycle_share_subscription_manage() {
        assertThat(registry.permissionForActionCode(ActionCode.SUBSCRIPTION_SUBSCRIBE))
                .isEqualTo(Permission.SUBSCRIPTION_MANAGE);
        assertThat(registry.permissionForActionCode(ActionCode.SUBSCRIPTION_CHANGE_STATUS))
                .isEqualTo(Permission.SUBSCRIPTION_MANAGE);
    }

    @Test
    void permissionForActionCode_resolves_every_action_code_to_non_null() {
        for (ActionCode code : ActionCode.values()) {
            assertThat(registry.permissionForActionCode(code))
                    .as("permissionForActionCode(%s)", code)
                    .isNotNull();
        }
    }
}
