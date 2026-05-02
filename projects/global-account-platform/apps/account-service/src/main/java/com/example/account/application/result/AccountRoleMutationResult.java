package com.example.account.application.result;

import java.time.Instant;
import java.util.List;

/**
 * TASK-BE-255: Result of a single-role add/remove operation. Carries the
 * complete role list after the mutation (not just the diff) so the response
 * mirrors the {@code AssignRolesResult} shape.
 *
 * <p>{@code changed} is {@code false} when the operation was a no-op (the role
 * was already present on add, or absent on remove). Callers can use it to
 * decide whether the response should be cached or whether downstream
 * notifications are needed.
 */
public record AccountRoleMutationResult(
        String accountId,
        String tenantId,
        List<String> roles,
        Instant updatedAt,
        boolean changed
) {
}
