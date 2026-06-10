package com.example.admin.application.exception;

/**
 * ADR-MONO-024 D3-i (TASK-BE-347) — thrown when {@code POST .../assignments/{tenantId}}
 * targets an (operator, tenant) pair that already has an {@code operator_tenant_assignment}
 * row. Maps to HTTP 409 {@code ASSIGNMENT_ALREADY_EXISTS} in {@code AdminExceptionHandler}.
 */
public class AssignmentAlreadyExistsException extends RuntimeException {

    public AssignmentAlreadyExistsException(String message) {
        super(message);
    }
}
