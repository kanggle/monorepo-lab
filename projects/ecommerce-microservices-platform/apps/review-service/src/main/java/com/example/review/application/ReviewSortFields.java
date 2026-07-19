package com.example.review.application;

import java.util.Set;

/**
 * Single source of truth for the review-listing sortable fields. Both the interface layer
 * (fail-fast 400 on an unknown sort field) and the infrastructure layer (defensive re-check
 * while building the {@code Sort}) validate against this allow-list, so it lives in one place
 * to avoid drift when a new sortable field is added.
 */
public final class ReviewSortFields {

    private static final Set<String> ALLOWED = Set.of("createdAt", "rating");

    private ReviewSortFields() {
    }

    /**
     * Extract the sort field named at the head of a {@code "field,dir"} spec and validate it
     * against the allow-list.
     *
     * @return the validated field name
     * @throws IllegalArgumentException if the field is not sortable
     */
    public static String requireValid(String sort) {
        String field = sort.split(",")[0].trim();
        if (!ALLOWED.contains(field)) {
            throw new IllegalArgumentException("Invalid sort field: " + field);
        }
        return field;
    }
}
