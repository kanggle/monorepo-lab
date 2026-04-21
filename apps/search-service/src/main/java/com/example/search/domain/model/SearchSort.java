package com.example.search.domain.model;

public enum SearchSort {
    RELEVANCE,
    PRICE_ASC,
    PRICE_DESC,
    NEWEST;

    public static SearchSort from(String value) {
        if (value == null) {
            return RELEVANCE;
        }
        return switch (value.toLowerCase()) {
            case "price_asc" -> PRICE_ASC;
            case "price_desc" -> PRICE_DESC;
            case "newest" -> NEWEST;
            default -> RELEVANCE;
        };
    }
}
