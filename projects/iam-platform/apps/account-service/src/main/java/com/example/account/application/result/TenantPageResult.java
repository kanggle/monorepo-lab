package com.example.account.application.result;

import com.example.account.domain.tenant.Tenant;
import org.springframework.data.domain.Page;

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
    public static TenantPageResult from(Page<Tenant> page) {
        List<TenantResult> items = page.getContent().stream()
                .map(TenantResult::from)
                .toList();
        return new TenantPageResult(
                items,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
