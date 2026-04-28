package com.wms.inventory.application.result;

import java.util.List;

/**
 * Generic paginated result. The REST layer maps it to the envelope shape
 * defined in {@code inventory-service-api.md} § Pagination.
 */
public record PageView<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        String sort
) {

    public PageView {
        content = List.copyOf(content);
    }

    public static <T> PageView<T> of(List<T> content, int page, int size,
                                     long totalElements, String sort) {
        int totalPages = size == 0 ? 0
                : (int) ((totalElements + size - 1) / size);
        return new PageView<>(content, page, size, totalElements, totalPages, sort);
    }
}
