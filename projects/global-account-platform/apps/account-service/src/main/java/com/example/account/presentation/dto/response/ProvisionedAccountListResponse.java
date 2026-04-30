package com.example.account.presentation.dto.response;

import com.example.account.application.result.ProvisionedAccountListResult;

import java.time.Instant;
import java.util.List;

/**
 * TASK-BE-231: Response DTO for GET /internal/tenants/{tenantId}/accounts.
 */
public record ProvisionedAccountListResponse(
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
    ) {
    }

    public static ProvisionedAccountListResponse from(ProvisionedAccountListResult result) {
        List<Item> items = result.content().stream()
                .map(i -> new Item(
                        i.accountId(),
                        i.tenantId(),
                        i.email(),
                        i.status(),
                        i.roles(),
                        i.createdAt()
                ))
                .toList();
        return new ProvisionedAccountListResponse(
                items,
                result.totalElements(),
                result.page(),
                result.size(),
                result.totalPages()
        );
    }
}
