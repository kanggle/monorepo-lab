package com.example.admin.application;

public enum ActionCode {
    ACCOUNT_LOCK,
    ACCOUNT_UNLOCK,
    SESSION_REVOKE,
    AUDIT_QUERY,
    // TASK-BE-029-2: operator self 2FA enrollment + verify. target_type=OPERATOR.
    OPERATOR_2FA_ENROLL,
    OPERATOR_2FA_VERIFY,
    // TASK-BE-113: operator self recovery-code regeneration. target_type=OPERATOR,
    // permission_used=auth.2fa_recovery_regenerate, twofa_used=FALSE.
    OPERATOR_2FA_RECOVERY_REGENERATE,
    // TASK-BE-029-3: operator self-login (password + optional 2FA). target_type=OPERATOR,
    // permission_used=auth.login, twofa_used is set per-outcome on the audit row.
    OPERATOR_LOGIN,
    // TASK-BE-040: operator refresh-token rotation + self-logout.
    // target_type=OPERATOR, target_id=operator_id, permission_used=auth.refresh|auth.logout.
    OPERATOR_REFRESH,
    OPERATOR_LOGOUT,
    // TASK-BE-054: GDPR/PIPA data rights.
    GDPR_DELETE,
    DATA_EXPORT,
    // TASK-BE-083: operator management (SUPER_ADMIN creates operators, changes roles/status).
    // target_type=OPERATOR, target_id=external operator_id (UUID v7),
    // permission_used=operator.manage.
    OPERATOR_CREATE,
    OPERATOR_ROLE_CHANGE,
    OPERATOR_STATUS_CHANGE
}
