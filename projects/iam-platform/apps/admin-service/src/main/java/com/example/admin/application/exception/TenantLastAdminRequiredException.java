package com.example.admin.application.exception;

/**
 * TASK-MONO-784 S4 (owner decision 2026-10-11 UTC «거절 (409)») — 409 {@code TENANT_LAST_ADMIN_REQUIRED}: the
 * write would leave a company with no {@code ACTIVE} operator holding {@code TENANT_ADMIN} there (from any
 * origin — direct, group fan-out or org-node row). Raised before anything is written by «이 회사에서 내보내기»,
 * the role PATCH, group member removal and group role revocation / group deletion, so none of them can be used
 * to route around the others.
 */
public class TenantLastAdminRequiredException extends RuntimeException {
    public TenantLastAdminRequiredException(String message) {
        super(message);
    }
}
