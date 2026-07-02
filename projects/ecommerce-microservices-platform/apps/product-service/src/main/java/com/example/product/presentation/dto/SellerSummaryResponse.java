package com.example.product.presentation.dto;

import com.example.product.application.dto.SellerPeriodSummary;

/**
 * Response body for {@code GET /api/admin/sellers/summary}.
 * JSON: { "today": long, "week": long, "month": long, "total": long }
 */
public record SellerSummaryResponse(long today, long week, long month, long total) {

    public static SellerSummaryResponse from(SellerPeriodSummary summary) {
        return new SellerSummaryResponse(summary.today(), summary.week(), summary.month(), summary.total());
    }
}
