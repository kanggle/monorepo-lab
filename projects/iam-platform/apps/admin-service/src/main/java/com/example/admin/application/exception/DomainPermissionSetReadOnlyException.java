package com.example.admin.application.exception;

/** 409 — a system preset cannot be edited or deleted; copy its roles into a tenant set instead. */
public class DomainPermissionSetReadOnlyException extends DomainPermissionSetException {
    public DomainPermissionSetReadOnlyException(String message) {
        super("DOMAIN_PERMISSION_SET_READ_ONLY", 409, message);
    }
}
