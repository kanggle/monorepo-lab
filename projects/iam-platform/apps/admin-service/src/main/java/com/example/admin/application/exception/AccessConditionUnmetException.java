package com.example.admin.application.exception;

/**
 * ADR-MONO-026 (axis ② 2단계) — thrown when an admin mutation passes RBAC but
 * fails the {@code SOURCE_IP} access condition (the request source IP is outside
 * the configured allowlist). Mapped to {@code 403 ACCESS_CONDITION_UNMET}.
 *
 * <p>Restriction-only: this is raised only AFTER the permission check has
 * granted; an access condition can never grant, only gate.
 */
public class AccessConditionUnmetException extends RuntimeException {
    public AccessConditionUnmetException(String message) {
        super(message);
    }
}
