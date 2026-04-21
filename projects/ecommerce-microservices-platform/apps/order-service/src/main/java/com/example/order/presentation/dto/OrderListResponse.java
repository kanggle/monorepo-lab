package com.example.order.presentation.dto;

import com.example.order.application.dto.OrderSummary;
import com.example.common.page.PageResult;

import java.time.Instant;
import java.util.List;

public record OrderListResponse(
        List<OrderSummaryItem> content,
        int page,
        int size,
        long totalElements
) {
    public static OrderListResponse from(PageResult<OrderSummary> pageResult) {
        List<OrderSummaryItem> items = pageResult.content().stream()
                .map(s -> new OrderSummaryItem(
                        s.orderId(), s.status(), s.totalPrice(), s.itemCount(), s.firstItemName(), s.createdAt()
                ))
                .toList();
        return new OrderListResponse(items, pageResult.page(), pageResult.size(), pageResult.totalElements());
    }

    public record OrderSummaryItem(
            String orderId,
            String status,
            long totalPrice,
            int itemCount,
            String firstItemName,
            Instant createdAt
    ) {}
}
