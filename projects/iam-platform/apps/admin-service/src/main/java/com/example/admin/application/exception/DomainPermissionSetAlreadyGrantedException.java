package com.example.admin.application.exception;

/**
 * 409 — the same set is already granted to this target FROM THIS SOURCE (a DIRECT row in that tenant, or the same
 * set on that group). Rows of other sources never trigger it — sources coexist.
 */
public class DomainPermissionSetAlreadyGrantedException extends DomainPermissionSetException {
    public DomainPermissionSetAlreadyGrantedException(String message) {
        super("DOMAIN_PERMISSION_SET_ALREADY_GRANTED", 409, message);
    }
}
