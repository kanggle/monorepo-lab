package com.wms.notification.domain.routing;

import com.wms.notification.domain.alert.AlertEnvelope;
import java.util.Map;

/**
 * Tiny dotted-segment JSON path resolver — sufficient for the v1 seeded
 * routing rules. Supports {@code $.payload.foo} and {@code $.payload.foo.bar}
 * traversal of nested {@code Map<String,Object>}. No array indexing, no
 * filter expressions.
 *
 * <p>The {@code $} root segment refers to the {@link AlertEnvelope}; the
 * top-level map exposes {@code eventId}, {@code eventType}, {@code payload}
 * etc. Most predicates traverse {@code $.payload.<field>}.
 */
final class JsonPathExtractor {

    private JsonPathExtractor() {}

    /**
     * Resolve {@code path} against {@code envelope}. Returns {@code null}
     * when any intermediate segment is missing.
     */
    static Object extract(AlertEnvelope envelope, String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String trimmed = path.startsWith("$.") ? path.substring(2) : path;
        if (trimmed.isEmpty()) {
            return null;
        }
        String[] segments = trimmed.split("\\.");
        Object current;
        if ("payload".equals(segments[0])) {
            current = envelope.payload();
        } else if ("eventId".equals(segments[0])) {
            current = envelope.eventId();
        } else if ("eventType".equals(segments[0])) {
            current = envelope.eventType();
        } else if ("aggregateId".equals(segments[0])) {
            current = envelope.aggregateId();
        } else {
            // Treat unknown root segment as payload-relative for ergonomics:
            // $.delta is interpreted as $.payload.delta.
            current = envelope.payload().get(segments[0]);
        }
        for (int i = 1; i < segments.length; i++) {
            if (current == null) {
                return null;
            }
            if (current instanceof Map<?, ?> map) {
                current = map.get(segments[i]);
            } else {
                return null;
            }
        }
        return current;
    }
}
