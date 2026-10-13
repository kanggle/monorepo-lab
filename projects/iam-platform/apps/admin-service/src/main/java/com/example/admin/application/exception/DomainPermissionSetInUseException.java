package com.example.admin.application.exception;

/** 409 — the set is still granted (operator rows of any source, or a group); grants are never removed implicitly. */
public class DomainPermissionSetInUseException extends DomainPermissionSetException {
    public DomainPermissionSetInUseException(String message) {
        super("DOMAIN_PERMISSION_SET_IN_USE", 409, message);
    }
}
