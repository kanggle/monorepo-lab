package com.example.admin.domain.rbac;

/**
 * Domain POJO for an admin operator. Framework-free.
 */
public record AdminOperator(
        String id,
        String email,
        String displayName,
        Status status,
        long version
) {
    public enum Status {
        ACTIVE, DISABLED, LOCKED
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }
}
