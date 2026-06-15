package com.example.admin.infrastructure.access;

import java.util.HashSet;
import java.util.Set;

/**
 * Shared helper for the {@code ResourceTagResolver}s.
 *
 * <p>Centralizes the comma-separated tags parsing that was previously duplicated
 * verbatim in {@code OperatorResourceTagResolver} and
 * {@code AbstractLocalResourceTagResolver}.
 */
final class ResourceTags {

    private ResourceTags() {
        // utility class
    }

    /** Split a comma-separated tags string into a set, dropping blanks. */
    static Set<String> splitTags(String raw) {
        Set<String> tags = new HashSet<>();
        if (raw == null) {
            return tags;
        }
        for (String part : raw.split(",")) {
            String t = part.trim();
            if (!t.isEmpty()) {
                tags.add(t);
            }
        }
        return tags;
    }
}
