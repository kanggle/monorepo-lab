package com.example.fanplatform.community.domain.post;

import java.util.List;

/**
 * Framework-free page slice returned by domain repository ports.
 *
 * <p>Keeps the {@code domain} layer independent of Spring Data's {@code Page} /
 * {@code Pageable} — infrastructure adapters convert between the JPA paging types
 * and this value object at the boundary. The {@link #numberOfElements()} and
 * {@link #hasNext()} derivations mirror Spring Data's {@code Page} semantics so
 * consumers are byte-compatible after the swap.
 *
 * @param content       this page's items
 * @param page          zero-based page number
 * @param size          requested page size
 * @param totalElements total number of matching items across all pages
 * @param totalPages    total number of pages
 * @param <T>           the item type
 */
public record PageResult<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    /** Number of elements on this page (mirrors {@code Page.getNumberOfElements}). */
    public int numberOfElements() {
        return content.size();
    }

    /** Whether a next page exists (mirrors {@code Page.hasNext}). */
    public boolean hasNext() {
        return page + 1 < totalPages;
    }
}
