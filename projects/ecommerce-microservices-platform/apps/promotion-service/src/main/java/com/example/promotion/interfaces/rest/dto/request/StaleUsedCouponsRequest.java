package com.example.promotion.interfaces.rest.dto.request;

import com.example.promotion.application.service.StaleUsedCouponQueryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Request body for {@code POST /api/internal/coupons/stale-used} (TASK-INT-028). Both fields are
 * optional; a missing field takes its default. Out-of-range values are 400 {@code VALIDATION_ERROR}.
 */
public record StaleUsedCouponsRequest(
        @Min(value = StaleUsedCouponQueryService.MIN_OLDER_THAN_MINUTES,
                message = "olderThanMinutes 는 30 이상이어야 합니다")
        Integer olderThanMinutes,
        @Min(value = 1, message = "limit 는 1 이상이어야 합니다")
        @Max(value = StaleUsedCouponQueryService.MAX_LIMIT, message = "limit 는 500 이하여야 합니다")
        Integer limit
) {

    public int resolvedOlderThanMinutes() {
        return olderThanMinutes != null ? olderThanMinutes : StaleUsedCouponQueryService.DEFAULT_OLDER_THAN_MINUTES;
    }

    public int resolvedLimit() {
        return limit != null ? limit : StaleUsedCouponQueryService.DEFAULT_LIMIT;
    }
}
