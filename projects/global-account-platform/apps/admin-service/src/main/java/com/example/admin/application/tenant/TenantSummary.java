package com.example.admin.application.tenant;

import java.time.Instant;

/**
 * TASK-BE-250: Domain result record for a single tenant, used across the admin-service
 * tenant use cases. Free of HTTP/framework types.
 */
public record TenantSummary(
        String tenantId,
        String displayName,
        String tenantType,
        String status,
        Instant createdAt,
        Instant updatedAt
) {}
