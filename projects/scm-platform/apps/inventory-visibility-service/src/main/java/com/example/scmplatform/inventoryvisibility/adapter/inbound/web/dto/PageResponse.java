package com.example.scmplatform.inventoryvisibility.adapter.inbound.web.dto;

import com.example.common.page.PageResult;

import java.util.List;

/**
 * Wire-boundary pagination envelope, sourced from the shared {@link PageResult}
 * (ADR-MONO-058 § D3 / TASK-SCM-BE-056). Adds {@code totalPages}, previously missing
 * from this service's hand-rolled shape — see
 * {@code specs/contracts/http/inventory-visibility-api.md} for the wire-shape record.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static <T> PageResponse<T> from(PageResult<T> result) {
        return new PageResponse<>(
                result.content(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages()
        );
    }
}
