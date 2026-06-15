package com.example.account.domain.repository;

import java.util.List;

/**
 * Framework-free page slice returned by domain repository ports.
 *
 * <p>Keeps the {@code domain} layer independent of Spring Data's {@code Page} /
 * {@code Pageable} — infrastructure adapters convert between the JPA paging types
 * and this value object at the boundary, mirroring the existing
 * {@code AccountRepository.ProvisioningPage} pattern.
 *
 * @param content       the page's items
 * @param totalElements total number of matching items across all pages
 * @param page          zero-based page number
 * @param size          requested page size
 * @param totalPages    total number of pages
 * @param <T>           the item type
 */
public record PageResult<T>(
        List<T> content,
        long totalElements,
        int page,
        int size,
        int totalPages
) {
}
