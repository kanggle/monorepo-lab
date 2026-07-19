package com.example.admin.application.exception;

/**
 * TASK-BE-520 / ADR-MONO-046 — 404 {@code GROUP_GRANT_NOT_FOUND}: the grant template does
 * not exist or belongs to another group.
 */
public class GroupGrantNotFoundException extends RuntimeException {
    public GroupGrantNotFoundException(String message) {
        super(message);
    }
}
