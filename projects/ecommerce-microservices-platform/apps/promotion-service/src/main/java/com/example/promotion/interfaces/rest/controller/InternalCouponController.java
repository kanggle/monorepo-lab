package com.example.promotion.interfaces.rest.controller;

import com.example.promotion.application.service.CouponCommandService;
import com.example.promotion.application.service.StaleUsedCouponQueryService;
import com.example.promotion.interfaces.rest.dto.request.ReleaseCouponRequest;
import com.example.promotion.interfaces.rest.dto.request.StaleUsedCouponsRequest;
import com.example.promotion.interfaces.rest.dto.response.StaleUsedCouponsResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service coupon operations for order-service (TASK-INT-026).
 *
 * <p>🔴 <b>Internal network only.</b> gateway-service routes {@code /api/promotions/**} and
 * {@code /api/coupons/**} — not {@code /api/internal/**} — and this path must never be added to
 * it: a user who could call release would free the coupon behind an order they were already
 * discounted for, and use it again. See {@code promotion-api.md}.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/internal/coupons")
public class InternalCouponController {

    private final CouponCommandService couponCommandService;
    private final StaleUsedCouponQueryService staleUsedCouponQueryService;

    /**
     * Coupons {@code USED} longer than {@code olderThanMinutes} (≥ 30), across tenants, for
     * batch-worker's orphan-coupon reconciliation (TASK-INT-028). Read-only.
     */
    @PostMapping("/stale-used")
    public ResponseEntity<StaleUsedCouponsResponse> staleUsed(
            @Valid @RequestBody(required = false) StaleUsedCouponsRequest request
    ) {
        StaleUsedCouponsRequest body = request != null ? request : new StaleUsedCouponsRequest(null, null);
        return ResponseEntity.ok(StaleUsedCouponsResponse.from(
                staleUsedCouponQueryService.find(body.resolvedOlderThanMinutes(), body.resolvedLimit())));
    }

    @PostMapping("/{couponId}/release")
    public ResponseEntity<Void> releaseCoupon(
            @PathVariable String couponId,
            @Valid @RequestBody ReleaseCouponRequest request
    ) {
        couponCommandService.releaseCoupon(couponId, request.orderId());
        return ResponseEntity.noContent().build();
    }
}
