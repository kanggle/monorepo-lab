package com.example.admin.application.exception;

/**
 * TASK-BE-477 / ADR-MONO-045 D2 — the acting-side tenant ({@code X-Tenant-Id}) is
 * outside the actor's {@code partnership.manage} admin-grant scope (the D2
 * TenantScopeGuard denied). Distinct error code from the operator-administration
 * surface's {@code TENANT_SCOPE_DENIED} per admin-api.md § Partnership Management
 * error table. Maps to HTTP 403 {@code PARTNERSHIP_SCOPE_DENIED}.
 */
public class PartnershipScopeDeniedException extends RuntimeException {

    public PartnershipScopeDeniedException(String message) {
        super(message);
    }
}
