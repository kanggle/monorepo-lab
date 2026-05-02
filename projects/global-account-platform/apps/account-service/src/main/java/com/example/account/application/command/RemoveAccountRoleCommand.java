package com.example.account.application.command;

/**
 * TASK-BE-255: Command to remove a single role from an account within a tenant.
 *
 * <p>Idempotent — the use case treats an absent role as a no-op and does not
 * emit an outbox event in that case.
 */
public record RemoveAccountRoleCommand(
        String tenantId,
        String accountId,
        String roleName,
        String operatorId
) {
}
