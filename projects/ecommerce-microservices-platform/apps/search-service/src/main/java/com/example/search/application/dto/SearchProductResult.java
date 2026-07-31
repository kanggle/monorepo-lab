package com.example.search.application.dto;

import com.example.common.page.PageResult;
import com.example.search.domain.model.FacetResult;
import com.example.search.domain.model.SearchDocument;

import java.util.List;

/**
 * Composes the shared {@link PageResult} paging carrier (content/page/size/
 * totalElements/totalPages) with the Elasticsearch-specific {@code facets}
 * aggregation, which is NOT part of {@code PageResult}'s shape and is kept
 * alongside it rather than forced into it (ADR-MONO-058 D3 — TASK-BE-567; a full
 * swap doesn't fit Elasticsearch's aggregation-driven facets — see the task's Edge
 * Cases). Delegate accessors preserve the previously-flat {@code content()}/
 * {@code totalElements()} call surface for existing consumers.
 */
public record SearchProductResult(
        PageResult<SearchDocument> pageResult,
        FacetResult facets
) {
    public List<SearchDocument> content() {
        return pageResult.content();
    }

    public long totalElements() {
        return pageResult.totalElements();
    }

    public int totalPages() {
        return pageResult.totalPages();
    }
}
