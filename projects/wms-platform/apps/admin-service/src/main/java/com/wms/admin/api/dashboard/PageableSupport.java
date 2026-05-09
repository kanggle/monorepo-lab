package com.wms.admin.api.dashboard;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/** Shared pageable parsing and ETag helper for all admin controllers. */
public final class PageableSupport {

    private PageableSupport() {}

    public static String etag(long version) {
        return "\"v" + version + "\"";
    }

    public static PageRequest pageable(int page, int size, String sort) {
        int comma = sort.indexOf(',');
        String field = comma < 0 ? sort : sort.substring(0, comma);
        String dir = comma < 0 ? "asc" : sort.substring(comma + 1);
        Sort.Direction direction = "desc".equalsIgnoreCase(dir) ? Sort.Direction.DESC : Sort.Direction.ASC;
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        return PageRequest.of(safePage, safeSize, Sort.by(direction, field));
    }
}
