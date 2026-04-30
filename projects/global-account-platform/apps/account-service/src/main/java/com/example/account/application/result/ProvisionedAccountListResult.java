package com.example.account.application.result;

import java.time.Instant;
import java.util.List;

/**
 * TASK-BE-231: Paginated list result for the provisioning GET /accounts endpoint.
 */
public record ProvisionedAccountListResult(
        List<Item> content,
        long totalElements,
        int page,
        int size,
        int totalPages
) {
    public record Item(
            String accountId,
            String tenantId,
            String email,
            String status,
            List<String> roles,
            Instant createdAt
    ) {}
}
