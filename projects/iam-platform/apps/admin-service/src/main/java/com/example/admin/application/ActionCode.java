package com.example.admin.application;

public enum ActionCode {
    ACCOUNT_LOCK,
    ACCOUNT_UNLOCK,
    SESSION_REVOKE,
    AUDIT_QUERY,
    // TASK-BE-357: operator account search/list (GET /api/admin/accounts). Used only
    // for the best-effort DENIED row when a cross-tenant `tenantId` is rejected
    // (403 TENANT_SCOPE_DENIED) — the success path is a read and is not audited.
    ACCOUNT_SEARCH,
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
    OPERATOR_STATUS_CHANGE,
    // TASK-BE-306: operator self-serve profile mutation (PATCH /api/admin/operators/me/profile).
    // target_type=OPERATOR, target_id=self.operator_id (UUID v7),
    // permission_used="<self_action>" (synthetic — no grantable permission;
    // self-flow audit row per admin-api.md §X-Operator-Reason in Exceptions sub-tree),
    // reason="<self_profile_update>", detail IS NULL (new value lives in the operator
    // row, not the audit detail column — R4/A3 invariant: audit subject is *that the
    // value changed*, not the value itself).
    OPERATOR_PROFILE_UPDATE,
    // TASK-BE-339 (ADR-MONO-020 D3 amendment follow-up): operator per-assignment
    // org_scope set/clear (PUT /api/admin/operators/{operatorId}/assignments/{tenantId}/org-scope).
    // target_type=OPERATOR, target_id=external operator_id (UUID v7),
    // target_tenant_id=path tenantId (the active tenant), permission_used=operator.manage.
    OPERATOR_ORG_SCOPE_UPDATE,
    // TASK-BE-250: tenant lifecycle management (SUPER_ADMIN only).
    // target_type=TENANT, target_id=tenantId, tenant_id='*' (platform scope),
    // target_tenant_id=affected tenantId, permission_used=tenant.manage.
    TENANT_CREATE,
    TENANT_SUSPEND,
    TENANT_REACTIVATE,
    TENANT_UPDATE,
    // TASK-BE-343 (ADR-MONO-023 D3): tenant↔domain subscription lifecycle
    // (operator-facing surface delegating to account-service). target_type=SUBSCRIPTION,
    // target_id="<tenantId>:<domainKey>", permission_used=subscription.manage.
    SUBSCRIPTION_SUBSCRIBE,
    SUBSCRIPTION_CHANGE_STATUS,
    // TASK-BE-347 (ADR-MONO-024 D3-i): operator↔tenant assignment create/remove
    // ("grant my employee access to my tenant"). target_type=OPERATOR,
    // target_id=external operator_id (UUID v7), target_tenant_id=assigned tenant,
    // permission_used=operator.manage.
    OPERATOR_ASSIGNMENT_CREATE,
    OPERATOR_ASSIGNMENT_DELETE,
    // TASK-BE-373 (ADR-MONO-034 U3 / step 3c): opt-in, audited, reversible operator↔
    // central-identity link. target_type=OPERATOR, target_id=external operator_id
    // (UUID v7), target_tenant_id=operator's home tenant, permission_used=operator.manage.
    // LINK sets admin_operators.identity_id (after email-match-necessary + fail-closed
    // identity resolve); UNLINK clears it (reversibility). Idempotent re-link to the
    // same identity is a no-op SUCCESS and still audited.
    OPERATOR_IDENTITY_LINK,
    OPERATOR_IDENTITY_UNLINK,
    // TASK-BE-477 (ADR-MONO-045 D2/D4): cross-org partnership lifecycle + participant
    // management. target_type=PARTNERSHIP, target_id=<partnershipId> (participant_*
    // rows carry target_id=<operatorId>), permission_used=partnership.manage.
    PARTNERSHIP_INVITE,
    PARTNERSHIP_ACCEPT,
    PARTNERSHIP_SUSPEND,
    PARTNERSHIP_REACTIVATE,
    PARTNERSHIP_TERMINATE,
    PARTNERSHIP_PARTICIPANT_ADD,
    PARTNERSHIP_PARTICIPANT_REMOVE,
    // TASK-BE-492 (ADR-MONO-047 D5): org-node tree mutations + ORG_ADMIN grant/revoke.
    // target_type=ORG_NODE, target_id=<orgNodeId> (the granted/revoked operator rides in
    // `detail` — the audit subject is the NODE, mirroring PARTNERSHIP_PARTICIPANT_*).
    // permission_used=org.manage. Reads (GET) write no audit row (BE-486 read-path rule).
    ORG_NODE_CREATE,
    ORG_NODE_UPDATE,
    ORG_NODE_DELETE,
    ORG_NODE_CEILING_SET,
    ORG_ADMIN_GRANT,
    ORG_ADMIN_REVOKE
}
