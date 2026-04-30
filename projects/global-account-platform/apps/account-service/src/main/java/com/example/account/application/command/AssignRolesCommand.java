package com.example.account.application.command;

import java.util.List;

/**
 * TASK-BE-231: Command to replace all roles for an account within a tenant.
 */
public record AssignRolesCommand(
        String tenantId,
        String accountId,
        List<String> roles,
        String operatorId
) {
}
