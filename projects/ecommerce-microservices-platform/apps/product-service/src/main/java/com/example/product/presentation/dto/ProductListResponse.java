package com.example.product.presentation.dto;

import com.example.product.application.dto.ProductListResult;
import com.example.product.application.dto.ProductSummary;
import com.example.product.presentation.support.UuidUtils;

import java.util.List;

public record ProductListResponse(
        List<ProductSummaryItem> content,
        int page,
        int size,
        long totalElements
) {
    public record ProductSummaryItem(
            String id,
            String name,
            String status,
            long price,
            String thumbnailUrl,
            String categoryId,
            String sellerId
    ) {}

    public static ProductListResponse from(ProductListResult result) {
        List<ProductSummaryItem> items = result.content().stream()
                .map(ProductListResponse::toItem)
                .toList();
        return new ProductListResponse(items, result.page(), result.size(), result.totalElements());
    }

    private static ProductSummaryItem toItem(ProductSummary summary) {
        return new ProductSummaryItem(
                summary.id().toString(),
                summary.name(),
                summary.status().name(),
                summary.price(),
                summary.thumbnailUrl(),
                UuidUtils.toString(summary.categoryId()),
                summary.sellerId());
    }
}
