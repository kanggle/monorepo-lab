package com.example.promotion.interfaces.rest.dto.response;

import com.example.promotion.application.result.CouponDetail;
import com.example.common.page.PageResult;

import java.util.List;

public record CouponListResponse(
        List<CouponItem> content,
        int page,
        int size,
        long totalElements
) {
    public static CouponListResponse from(PageResult<CouponDetail> result) {
        List<CouponItem> items = result.content().stream()
                .map(CouponItem::from)
                .toList();
        return new CouponListResponse(items, result.page(), result.size(), result.totalElements());
    }

    public record CouponItem(
            String couponId,
            String promotionId,
            String promotionName,
            String discountType,
            long discountValue,
            long maxDiscountAmount,
            String status,
            String issuedAt,
            String expiresAt
    ) {
        public static CouponItem from(CouponDetail detail) {
            return new CouponItem(
                    detail.couponId(),
                    detail.promotionId(),
                    detail.promotionName(),
                    detail.discountType() != null ? detail.discountType().name() : null,
                    detail.discountValue(),
                    detail.maxDiscountAmount(),
                    detail.status().name(),
                    detail.issuedAt().toString(),
                    detail.expiresAt() != null ? detail.expiresAt().toString() : null
            );
        }
    }
}
