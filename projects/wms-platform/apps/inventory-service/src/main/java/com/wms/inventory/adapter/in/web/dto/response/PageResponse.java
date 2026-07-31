package com.wms.inventory.adapter.in.web.dto.response;

import com.example.common.page.PageResult;
import java.util.List;
import java.util.function.Function;

/**
 * REST envelope for paginated lists per
 * {@code inventory-service-api.md} § Pagination.
 */
public record PageResponse<T>(
        List<T> content,
        PageMeta page,
        String sort
) {
    public static <D, T> PageResponse<T> from(PageResult<D> result, String sort, Function<D, T> mapper) {
        List<T> content = result.content().stream().map(mapper).toList();
        PageMeta meta = new PageMeta(result.page(), result.size(),
                result.totalElements(), result.totalPages());
        return new PageResponse<>(content, meta, sort);
    }

    public record PageMeta(int number, int size, long totalElements, int totalPages) {
    }
}
