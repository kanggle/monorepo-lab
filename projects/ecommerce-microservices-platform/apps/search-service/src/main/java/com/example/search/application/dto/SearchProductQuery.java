package com.example.search.application.dto;

import com.example.common.page.PageQuery;
import com.example.search.domain.model.SearchFilter;
import com.example.search.domain.model.SearchSort;

/**
 * {@code page}/{@code size} are backed by the shared {@link PageQuery}
 * (ADR-MONO-058 D3 — TASK-BE-567): {@code ElasticsearchQueryAdapter} computes an
 * offset/limit ES {@code from}/{@code size} directly from it, so the request side
 * fits the shared carrier exactly. {@code sort} stays outside {@code PageQuery} —
 * Elasticsearch relevance/price sort is a different vocabulary than PageQuery's
 * generic {@code sortBy}/{@code sortDirection} column-sort model, so those two
 * PageQuery fields are simply unused here (left {@code null} by callers) rather than
 * forced to carry search-specific sort semantics.
 */
public record SearchProductQuery(
        SearchFilter filter,
        SearchSort sort,
        PageQuery pageQuery
) {
    public int page() {
        return pageQuery.page();
    }

    public int size() {
        return pageQuery.size();
    }
}
