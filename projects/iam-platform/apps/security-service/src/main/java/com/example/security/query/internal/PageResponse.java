package com.example.security.query.internal;

import org.springframework.data.domain.Page;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the standard internal pagination envelope
 * ({@code content / page / size / totalElements / totalPages}) shared by the
 * read-only {@code /internal/security/**} query controllers.
 */
final class PageResponse {

    private PageResponse() {
    }

    static Map<String, Object> of(Page<?> page) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("content", page.getContent());
        response.put("page", page.getNumber());
        response.put("size", page.getSize());
        response.put("totalElements", page.getTotalElements());
        response.put("totalPages", page.getTotalPages());
        return response;
    }
}
