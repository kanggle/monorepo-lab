package com.example.fanplatform.artist.adapter.in.web.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Helper for offset-pagination meta blocks. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class PageMeta {

    private PageMeta() {}

    public static Map<String, Object> of(int page, int size, long totalElements, int totalPages) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("timestamp", Instant.now().toString());
        meta.put("page", page);
        meta.put("size", size);
        meta.put("totalElements", totalElements);
        meta.put("totalPages", totalPages);
        return meta;
    }
}
