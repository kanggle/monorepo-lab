package com.wms.inventory.adapter.in.web.dto.response;

import com.wms.inventory.application.result.PageView;
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
    public static <D, T> PageResponse<T> from(PageView<D> view, Function<D, T> mapper) {
        List<T> content = view.content().stream().map(mapper).toList();
        PageMeta meta = new PageMeta(view.page(), view.size(),
                view.totalElements(), view.totalPages());
        return new PageResponse<>(content, meta, view.sort());
    }

    public record PageMeta(int number, int size, long totalElements, int totalPages) {
    }
}
