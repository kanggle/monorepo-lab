package com.example.scmplatform.demandplanning.adapter.inbound.web.dto;

import com.example.common.page.PageResult;

/**
 * Pagination metadata for list responses.
 *
 * <p>Deliberately kept as its own record rather than reused wholesale as
 * {@link PageResult} (ADR-MONO-058 § D3 / TASK-SCM-BE-056 wire-shape decision,
 * recorded in {@code specs/contracts/http/demand-planning-api.md}): this service's
 * {@code ApiEnvelope} convention places page content in the top-level {@code data}
 * array and only the pagination metadata in {@code meta} (a data/meta split, unlike
 * {@code procurement-service}/{@code inventory-visibility-service}'s single-object
 * {@code content + page + size + ...} shape) — {@link PageResult} itself always
 * carries {@code content}, so embedding it directly in {@code meta} would duplicate
 * the page rows in both {@code data} and {@code meta}. {@link #from} still *sources*
 * every field from the shared {@link PageResult}, satisfying the ADR's adoption
 * mandate without wrapping/duplicating its fields independently.
 */
public record PageMeta(int page, int size, long totalElements, int totalPages) {

    public static PageMeta from(PageResult<?> result) {
        return new PageMeta(result.page(), result.size(), result.totalElements(), result.totalPages());
    }
}
