package com.example.product.application.dto;

import java.util.List;

/**
 * Paged seller list result (ADR-MONO-030 Step 4 facet f). Mirrors the
 * {@code ProductListResult} envelope so the presentation layer maps it the same
 * way ({@code content[]}, {@code page}, {@code size}, {@code totalElements}).
 */
public record SellerListResult(
        List<SellerSummary> content,
        int page,
        int size,
        long totalElements
) {}
