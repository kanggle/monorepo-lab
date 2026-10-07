package com.example.admin.application.exception;

/**
 * TASK-BE-625 (ADR-MONO-047 § 개정 2026-10-07) — 409 {@code TENANT_ORG_NODE_CONFLICT}.
 *
 * <p>The tenant's placement changed between admin-service's two-sided check and the write:
 * account-service refused the write because the tenant is no longer at the source the actor
 * was authorized against. Nothing was written; the caller re-reads and retries (and is then
 * checked against the new source).
 */
public class TenantOrgNodeConflictException extends AccountBusinessException {
    public TenantOrgNodeConflictException(String message) {
        super(message);
    }
}
