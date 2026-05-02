package com.example.account.application.result;

import com.example.account.domain.tenant.Tenant;

import java.time.Instant;

/**
 * TASK-BE-250: Application-layer result for a single tenant.
 * Free of framework/HTTP-layer types; mapped to response DTOs in the presentation layer.
 */
public record TenantResult(
        String tenantId,
        String displayName,
        String tenantType,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
    public static TenantResult from(Tenant tenant) {
        return new TenantResult(
                tenant.getTenantId().value(),
                tenant.getDisplayName(),
                tenant.getTenantType().name(),
                tenant.getStatus().name(),
                tenant.getCreatedAt(),
                tenant.getUpdatedAt()
        );
    }
}
