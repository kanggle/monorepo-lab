package com.example.common.page;

import java.util.List;
import java.util.function.Function;

/**
 * Common pagination result. Used across services as a cross-service technical DTO.
 *
 * @param <T> the type of content items
 */
public record PageResult<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    /**
     * Maps the content of this PageResult to a different type.
     */
    public <R> PageResult<R> map(Function<T, R> mapper) {
        return new PageResult<>(
                content.stream().map(mapper).toList(),
                page,
                size,
                totalElements,
                totalPages
        );
    }
}
