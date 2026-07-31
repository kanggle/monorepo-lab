package com.wms.inbound.adapter.in.rest.dto;

import com.example.common.page.PageResult;
import java.util.List;
import java.util.function.Function;

/**
 * HTTP pagination envelope matching
 * {@code specs/contracts/http/inbound-service-api.md} §Pagination.
 */
public record PageResponse<T>(
        List<T> content,
        PageMeta page,
        String sort) {

    public record PageMeta(int number, int size, long totalElements, int totalPages) {
    }

    public static <S, T> PageResponse<T> from(PageResult<S> result, String sort, Function<S, T> mapper) {
        return new PageResponse<>(
                result.content().stream().map(mapper).toList(),
                new PageMeta(result.page(), result.size(), result.totalElements(), result.totalPages()),
                sort);
    }
}
