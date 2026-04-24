package com.example.promotion.interfaces.rest.dto.response;

import com.example.promotion.application.result.PromotionSummary;
import com.example.common.page.PageResult;

import java.util.List;

public record PromotionListResponse(
        List<PromotionSummaryItem> content,
        int page,
        int size,
        long totalElements
) {
    public static PromotionListResponse from(PageResult<PromotionSummary> result) {
        List<PromotionSummaryItem> items = result.content().stream()
                .map(PromotionSummaryItem::from)
                .toList();
        return new PromotionListResponse(items, result.page(), result.size(), result.totalElements());
    }

    public record PromotionSummaryItem(
            String promotionId,
            String name,
            String discountType,
            long discountValue,
            int maxIssuanceCount,
            int issuedCount,
            String startDate,
            String endDate,
            String status
    ) {
        public static PromotionSummaryItem from(PromotionSummary summary) {
            return new PromotionSummaryItem(
                    summary.promotionId(),
                    summary.name(),
                    summary.discountType().name(),
                    summary.discountValue(),
                    summary.maxIssuanceCount(),
                    summary.issuedCount(),
                    summary.startDate().toString(),
                    summary.endDate().toString(),
                    summary.status().name()
            );
        }
    }
}
