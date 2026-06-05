package com.example.account.application.command;

import java.util.List;

/**
 * TASK-BE-231: Command for the internal provisioning API to create a new account
 * under a specific tenant.
 */
public record ProvisionAccountCommand(
        String tenantId,
        String email,
        String password,
        String displayName,
        String locale,
        String timezone,
        List<String> roles,
        String operatorId
) {
}
