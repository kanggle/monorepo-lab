package com.example.admin.application.exception;

/**
 * TASK-BE-339 (ADR-MONO-020 D3 amendment follow-up) — thrown when no
 * {@code operator_tenant_assignment} row exists for a (operator, tenant) pair
 * targeted by an org_scope set/clear request. The {@code org_scope} attribute
 * lives only on an explicit assignment row; row creation/deletion (assigning an
 * operator to a tenant) is out of scope for this task.
 *
 * <p>Maps to HTTP 404 with error code {@code ASSIGNMENT_NOT_FOUND} in
 * {@code AdminExceptionHandler}.
 */
public class AssignmentNotFoundException extends RuntimeException {

    public AssignmentNotFoundException(String message) {
        super(message);
    }
}
