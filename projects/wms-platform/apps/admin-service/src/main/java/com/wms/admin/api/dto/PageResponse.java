package com.wms.admin.api.dto;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * Pagination envelope per
 * {@code admin-service-api.md § Global Conventions § Pagination}.
 */
public record PageResponse<T>(
        List<T> content,
        PageMetadata page,
        String sort) {

    public record PageMetadata(int number, int size, long totalElements, int totalPages) {
    }

    public static <T, R> PageResponse<R> from(Page<T> page, String sort, Function<T, R> mapper) {
        List<R> content = page.getContent().stream().map(mapper).toList();
        return new PageResponse<>(
                content,
                new PageMetadata(page.getNumber(), page.getSize(),
                        page.getTotalElements(), page.getTotalPages()),
                sort);
    }
}
