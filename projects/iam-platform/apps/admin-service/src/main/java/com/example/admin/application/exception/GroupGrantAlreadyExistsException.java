package com.example.admin.application.exception;

/**
 * TASK-BE-520 / ADR-MONO-046 — 409 {@code GROUP_GRANT_ALREADY_EXISTS}: the same
 * {@code (group, type, role/tenant)} grant template already exists.
 */
public class GroupGrantAlreadyExistsException extends RuntimeException {
    public GroupGrantAlreadyExistsException(String message) {
        super(message);
    }
}
