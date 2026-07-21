package com.example.erp.masterdata.domain.common;

import java.util.List;

/**
 * A page of aggregate rows plus the TRUE total-row count matching the query
 * (masterdata-api.md § PageMeta). {@code totalElements} is the count across ALL
 * pages of the filtered result — NOT {@code content.size()} — so a caller on
 * page 0 of a 25-row result sees {@code totalElements == 25} even when
 * {@code content} holds only the page-size slice.
 *
 * <p>Pure Java — no framework imports (domain layer boundary rule).
 */
public record PageResult<T>(List<T> content, long totalElements) {

    /** Re-project the page content to a view type while preserving the total. */
    public <R> PageResult<R> map(java.util.function.Function<? super T, ? extends R> mapper) {
        return new PageResult<>(content.stream().<R>map(mapper).toList(), totalElements);
    }
}
