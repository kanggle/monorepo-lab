package com.example.account.application.command;

/**
 * TASK-BE-255: Command to add a single role to an account within a tenant.
 *
 * <p>Idempotent — the use case treats an already-present role as a no-op
 * and does not emit an outbox event in that case.
 */
public record AddAccountRoleCommand(
        String tenantId,
        String accountId,
        String roleName,
        String operatorId
) {
}
