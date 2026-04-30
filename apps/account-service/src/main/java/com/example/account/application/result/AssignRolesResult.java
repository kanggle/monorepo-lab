package com.example.account.application.result;

import java.time.Instant;
import java.util.List;

/**
 * TASK-BE-231: Result of a successful roles-replace operation.
 */
public record AssignRolesResult(
        String accountId,
        String tenantId,
        List<String> roles,
        Instant updatedAt
) {
}
