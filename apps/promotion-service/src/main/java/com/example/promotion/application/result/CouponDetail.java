package com.example.promotion.application.result;

import com.example.promotion.domain.coupon.Coupon;
import com.example.promotion.domain.coupon.CouponStatus;
import com.example.promotion.domain.promotion.DiscountType;

import java.time.Instant;

public record CouponDetail(
        String couponId,
        String promotionId,
        String promotionName,
        DiscountType discountType,
        long discountValue,
        long maxDiscountAmount,
        CouponStatus status,
        Instant issuedAt,
        Instant expiresAt
) {
    public static CouponDetail from(Coupon coupon, String promotionName,
                                     DiscountType discountType, long discountValue,
                                     long maxDiscountAmount) {
        return new CouponDetail(
                coupon.getCouponId(),
                coupon.getPromotionId(),
                promotionName,
                discountType,
                discountValue,
                maxDiscountAmount,
                coupon.getStatus(),
                coupon.getIssuedAt(),
                coupon.getExpiresAt()
        );
    }
}
