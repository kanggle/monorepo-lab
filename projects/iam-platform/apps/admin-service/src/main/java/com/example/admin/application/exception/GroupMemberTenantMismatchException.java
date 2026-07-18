package com.example.admin.application.exception;

/**
 * TASK-BE-520 / ADR-MONO-046 D3 — 422 {@code GROUP_MEMBER_TENANT_MISMATCH}: the target
 * operator's home tenant differs from the group's tenant. A group holds only its own
 * tenant's operators (else fan-out would cross the tenant boundary).
 */
public class GroupMemberTenantMismatchException extends RuntimeException {
    public GroupMemberTenantMismatchException(String message) {
        super(message);
    }
}
