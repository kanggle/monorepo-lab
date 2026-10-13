package com.example.admin.application.exception;

/**
 * 404 — the set does not exist, belongs to another tenant (not disclosed), is a tenant set where only a preset may be
 * used ({@code '*'} grant row), or — on a personal revoke — there is no DIRECT / MIGRATION row of it to remove.
 */
public class DomainPermissionSetNotFoundException extends DomainPermissionSetException {
    public DomainPermissionSetNotFoundException(String message) {
        super("DOMAIN_PERMISSION_SET_NOT_FOUND", 404, message);
    }
}
