package com.example.admin.application.exception;

/**
 * TASK-BE-636 ③ (owner decision 2026-10-14 UTC) — 422 {@code ORG_ADMIN_GRANT_TARGET_NOT_MEMBER}.
 *
 * <p>Raised when a node-scoped grant would record its row under the target operator's home tenant
 * ({@code admin_operators.tenant_id}) and that tenant is not one of the target's memberships
 * ({@code operator_tenant_assignment}) — since TASK-MONO-784 a person removed from the home company («이 회사에서
 * 내보내기») while a member elsewhere keeps the home. Writing the row would break 784 D-1 («a non-'*' role row's
 * tenant is one of the person's memberships»).
 *
 * <p>Nothing is written: the row is not moved to another membership, and no membership is re-created in the home
 * (that would be a silent re-joining of a company the person was removed from). Checked after the ceiling (admin-api.md
 * § POST …/org-nodes/{id}/admins: 404 → 403 ≤-own → 422 ceiling → this). A {@code '*'} operator (no membership) is
 * never subject to it.
 */
public class OrgAdminGrantTargetNotMemberException extends RuntimeException {
    public OrgAdminGrantTargetNotMemberException(String message) {
        super(message);
    }
}
