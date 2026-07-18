package com.example.admin.application.exception;

/**
 * TASK-BE-520 / ADR-MONO-046 — 409 {@code GROUP_MEMBER_ALREADY_EXISTS}: the
 * {@code (group, operator)} membership already exists.
 */
public class GroupMemberAlreadyExistsException extends RuntimeException {
    public GroupMemberAlreadyExistsException(String message) {
        super(message);
    }
}
