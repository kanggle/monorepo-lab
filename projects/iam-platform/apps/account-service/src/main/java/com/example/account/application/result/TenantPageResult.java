package com.example.account.application.result;

import com.example.account.domain.repository.PageResult;
import com.example.account.domain.tenant.Tenant;

import java.util.List;

/**
 * TASK-BE-250: Paginated result for tenant list queries.
 */
public record TenantPageResult(
        List<TenantResult> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static TenantPageResult from(PageResult<Tenant> page) {
        List<TenantResult> items = page.content().stream()
                .map(TenantResult::from)
                .toList();
        return new TenantPageResult(
                items,
                page.page(),
                page.size(),
                page.totalElements(),
                page.totalPages()
        );
    }
}
