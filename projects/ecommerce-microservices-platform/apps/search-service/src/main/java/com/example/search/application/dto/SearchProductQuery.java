package com.example.search.application.dto;

import com.example.search.domain.model.SearchFilter;
import com.example.search.domain.model.SearchSort;

public record SearchProductQuery(
        SearchFilter filter,
        SearchSort sort,
        int page,
        int size
) {
    private static final int MAX_SIZE = 100;

    public SearchProductQuery {
        if (size <= 0) {
            throw new IllegalArgumentException("size must be greater than 0");
        }
        size = Math.min(size, MAX_SIZE);
    }
}
