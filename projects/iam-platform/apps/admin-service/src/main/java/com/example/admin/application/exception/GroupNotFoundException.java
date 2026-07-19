package com.example.admin.application.exception;

/**
 * TASK-BE-520 / ADR-MONO-046 — 404 {@code GROUP_NOT_FOUND}. Raised both when the group does
 * not exist AND (on read paths) when it lies outside the actor's tenant scope — the read
 * surface is enumeration-safe (a 403 would confirm existence). On mutation paths the group
 * is loaded first (404 if absent) and the D3 {@code TenantScopeGuard} then yields 403
 * {@code TENANT_SCOPE_DENIED} for an existing out-of-scope group.
 */
public class GroupNotFoundException extends RuntimeException {
    public GroupNotFoundException(String message) {
        super(message);
    }
}
