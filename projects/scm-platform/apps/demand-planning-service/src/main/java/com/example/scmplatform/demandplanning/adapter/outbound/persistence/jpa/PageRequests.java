package com.example.scmplatform.demandplanning.adapter.outbound.persistence.jpa;

import com.example.common.page.PageQuery;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/**
 * Converts the framework-free {@link PageQuery} into a Spring Data {@link PageRequest},
 * preserving the sort field + direction when present.
 *
 * <p>Mirrors {@code procurement-service}'s
 * {@code infrastructure.persistence.jpa.PageRequests} (ADR-MONO-058 § D3 /
 * TASK-SCM-BE-056 — same conversion, kept per-service rather than promoted to
 * {@code libs/} because it is a thin JPA-boundary adapter, not a cross-service
 * technical DTO).
 */
public final class PageRequests {

    private PageRequests() {
    }

    public static PageRequest toPageable(PageQuery pageQuery) {
        if (pageQuery.sortBy() == null || pageQuery.sortBy().isBlank()) {
            return PageRequest.of(pageQuery.page(), pageQuery.size());
        }
        Sort.Direction direction = "desc".equalsIgnoreCase(pageQuery.sortDirection())
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;
        return PageRequest.of(pageQuery.page(), pageQuery.size(),
                Sort.by(direction, pageQuery.sortBy()));
    }
}
