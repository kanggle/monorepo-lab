package com.example.search.adapter.outbound.elasticsearch;

import com.example.search.domain.model.SearchDocument;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
final class ElasticsearchFieldMapper {

    private ElasticsearchFieldMapper() {
    }

    static SearchDocument toSearchDocument(Map<String, Object> source, Double score) {
        return new SearchDocument(
                getString(source, "productId"),
                getString(source, "name"),
                getString(source, "description"),
                toLong(source.get("price")),
                getString(source, "status"),
                getString(source, "categoryId"),
                toInt(source.get("totalStock")),
                getString(source, "thumbnailUrl"),
                score
        );
    }

    static String getString(Map<String, Object> source, String key) {
        Object val = source.get(key);
        return val != null ? val.toString() : null;
    }

    static long toLong(Object val) {
        if (val == null) return 0L;
        if (val instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(val.toString());
        } catch (NumberFormatException e) {
            log.warn("Failed to parse long value: {}", val);
            return 0L;
        }
    }

    static int toInt(Object val) {
        if (val == null) return 0;
        if (val instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(val.toString());
        } catch (NumberFormatException e) {
            log.warn("Failed to parse int value: {}", val);
            return 0;
        }
    }
}
