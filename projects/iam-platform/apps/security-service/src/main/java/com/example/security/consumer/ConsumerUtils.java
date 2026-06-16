package com.example.security.consumer;

/**
 * Shared helpers for the Kafka consumer transport layer.
 *
 * <p>Package-private — only the {@code consumer/} adapters use these. Note that
 * {@code AuthEventMapper} deliberately keeps its own, slightly different
 * {@code firstNonBlank} (it does <em>not</em> treat the literal string {@code "null"}
 * as blank), so it is not migrated here.
 */
final class ConsumerUtils {

    private ConsumerUtils() {
    }

    /**
     * Returns the first value that is non-null, non-blank, and not the literal string
     * {@code "null"} (a JSON text node can stringify a JSON {@code null} to {@code "null"}).
     * Returns {@code null} when none qualify.
     */
    static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank() && !"null".equals(v)) return v;
        }
        return null;
    }
}
