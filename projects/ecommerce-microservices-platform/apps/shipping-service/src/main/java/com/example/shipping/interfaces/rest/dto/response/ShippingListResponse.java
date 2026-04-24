package com.example.shipping.interfaces.rest.dto.response;

import com.example.shipping.application.result.ShippingSummary;
import com.example.common.page.PageResult;

import java.util.List;

public record ShippingListResponse(
        List<ShippingListItem> content,
        int page,
        int size,
        long totalElements
) {
    public static ShippingListResponse from(PageResult<ShippingSummary> pageResult) {
        List<ShippingListItem> items = pageResult.content().stream()
                .map(s -> new ShippingListItem(
                        s.shippingId(),
                        s.orderId(),
                        s.status().name(),
                        s.trackingNumber(),
                        s.carrier(),
                        s.createdAt().toString(),
                        s.updatedAt().toString()
                ))
                .toList();
        return new ShippingListResponse(items, pageResult.page(), pageResult.size(), pageResult.totalElements());
    }

    public record ShippingListItem(
            String shippingId,
            String orderId,
            String status,
            String trackingNumber,
            String carrier,
            String createdAt,
            String updatedAt
    ) {}
}
