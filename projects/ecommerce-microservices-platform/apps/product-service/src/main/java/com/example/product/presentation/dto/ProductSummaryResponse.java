package com.example.product.presentation.dto;

import com.example.product.application.dto.ProductPeriodSummary;

/**
 * Response body for {@code GET /api/admin/products/summary}.
 * JSON: { "today": long, "week": long, "month": long, "total": long }
 */
public record ProductSummaryResponse(long today, long week, long month, long total) {

    public static ProductSummaryResponse from(ProductPeriodSummary summary) {
        return new ProductSummaryResponse(summary.today(), summary.week(), summary.month(), summary.total());
    }
}
