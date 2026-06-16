package com.example.scmplatform.procurement.infrastructure.persistence.jpa;

import com.example.common.page.PageQuery;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/**
 * Converts the framework-free {@link PageQuery} into a Spring Data {@link PageRequest},
 * preserving the sort field + direction.
 *
 * <p>Lives in {@code infrastructure} so the domain repository port can stay framework-free
 * (it speaks {@link PageQuery} / {@code PageResult}, never Spring Data's {@code Pageable}).
 * The sort preservation is the TASK-SCM-BE-016 L5 fix — without it the sort fields were
 * silently discarded.
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
