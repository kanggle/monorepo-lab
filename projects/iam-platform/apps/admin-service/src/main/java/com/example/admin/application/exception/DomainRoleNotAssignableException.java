package com.example.admin.application.exception;

/** 422 — a set names a role outside the assignable domain-role catalog (administrative tiers included). */
public class DomainRoleNotAssignableException extends DomainPermissionSetException {
    public DomainRoleNotAssignableException(String message) {
        super("DOMAIN_ROLE_NOT_ASSIGNABLE", 422, message);
    }
}
