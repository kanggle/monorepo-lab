package com.example.promotion.interfaces.rest.controller;

import com.example.promotion.application.exception.InvalidCouponStatusException;
import com.example.promotion.application.exception.InvalidPromotionStatusException;
import com.example.promotion.domain.coupon.CouponStatus;
import com.example.promotion.domain.promotion.PromotionStatus;

final class PromotionControllerUtils {

    private PromotionControllerUtils() {
    }

    static CouponStatus parseCouponStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return CouponStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw new InvalidCouponStatusException(status);
        }
    }

    static PromotionStatus parsePromotionStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return PromotionStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw new InvalidPromotionStatusException(status);
        }
    }
}
