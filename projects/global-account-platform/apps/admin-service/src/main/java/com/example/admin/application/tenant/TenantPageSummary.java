package com.example.admin.application.tenant;

import java.util.List;

/**
 * TASK-BE-250: Paginated result for tenant list queries in admin-service.
 */
public record TenantPageSummary(
        List<TenantSummary> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {}
