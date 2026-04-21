package com.example.order.presentation.dto;

import com.example.order.application.dto.AdminOrderSummary;
import com.example.common.page.PageResult;

import java.time.Instant;
import java.util.List;

public record AdminOrderListResponse(
        List<AdminOrderSummaryItem> content,
        int page,
        int size,
        long totalElements
) {
    public static AdminOrderListResponse from(PageResult<AdminOrderSummary> pageResult) {
        List<AdminOrderSummaryItem> items = pageResult.content().stream()
                .map(s -> new AdminOrderSummaryItem(
                        s.orderId(), s.userId(), s.status(),
                        s.totalPrice(), s.itemCount(), s.firstItemName(), s.createdAt()
                ))
                .toList();
        return new AdminOrderListResponse(items, pageResult.page(), pageResult.size(), pageResult.totalElements());
    }

    public record AdminOrderSummaryItem(
            String orderId,
            String userId,
            String status,
            long totalPrice,
            int itemCount,
            String firstItemName,
            Instant createdAt
    ) {}
}
