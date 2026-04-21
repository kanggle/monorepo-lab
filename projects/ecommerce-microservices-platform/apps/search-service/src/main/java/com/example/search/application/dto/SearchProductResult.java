package com.example.search.application.dto;

import com.example.search.domain.model.FacetResult;
import com.example.search.domain.model.SearchDocument;

import java.util.List;

public record SearchProductResult(
        List<SearchDocument> content,
        FacetResult facets,
        long totalElements
) {
}
