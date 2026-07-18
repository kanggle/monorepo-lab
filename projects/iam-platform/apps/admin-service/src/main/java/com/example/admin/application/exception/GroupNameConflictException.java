package com.example.admin.application.exception;

/**
 * TASK-BE-520 / ADR-MONO-046 — 409 {@code GROUP_NAME_CONFLICT}: a group with the same
 * {@code (tenant_id, name)} already exists (create or rename).
 */
public class GroupNameConflictException extends RuntimeException {
    public GroupNameConflictException(String message) {
        super(message);
    }
}
