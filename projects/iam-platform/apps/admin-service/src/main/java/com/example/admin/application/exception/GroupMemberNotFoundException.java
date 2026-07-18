package com.example.admin.application.exception;

/**
 * TASK-BE-520 / ADR-MONO-046 — 404 {@code GROUP_MEMBER_NOT_FOUND}: no
 * {@code (group, operator)} membership to remove.
 */
public class GroupMemberNotFoundException extends RuntimeException {
    public GroupMemberNotFoundException(String message) {
        super(message);
    }
}
