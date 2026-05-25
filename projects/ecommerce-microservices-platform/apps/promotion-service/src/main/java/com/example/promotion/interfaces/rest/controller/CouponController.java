package com.example.promotion.interfaces.rest.controller;

import com.example.promotion.application.command.ApplyCouponCommand;
import com.example.promotion.application.result.ApplyCouponResult;
import com.example.promotion.application.result.CouponDetail;
import com.example.promotion.application.service.CouponCommandService;
import com.example.promotion.application.service.CouponQueryService;
import com.example.promotion.domain.coupon.CouponStatus;
import com.example.common.page.PageResult;
import com.example.promotion.interfaces.rest.dto.request.ApplyCouponRequest;
import com.example.promotion.interfaces.rest.dto.response.ApplyCouponResponse;
import com.example.promotion.interfaces.rest.dto.response.CouponListResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/coupons")
public class CouponController {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final CouponCommandService couponCommandService;
    private final CouponQueryService couponQueryService;

    @GetMapping("/me")
    public ResponseEntity<CouponListResponse> getMyCoupons(
            @RequestHeader("X-User-Id") @NotBlank(message = "X-User-Id 헤더는 필수입니다") String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = size < 1 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        CouponStatus couponStatus = PromotionControllerUtils.parseCouponStatus(status);

        PageResult<CouponDetail> result = couponQueryService.getMyCoupons(userId, safePage, safeSize, couponStatus);
        return ResponseEntity.ok(CouponListResponse.from(result));
    }

    @PostMapping("/{couponId}/apply")
    public ResponseEntity<ApplyCouponResponse> applyCoupon(
            @RequestHeader("X-User-Id") @NotBlank(message = "X-User-Id 헤더는 필수입니다") String userId,
            @PathVariable String couponId,
            @Valid @RequestBody ApplyCouponRequest request
    ) {
        ApplyCouponCommand command = new ApplyCouponCommand(
                couponId, userId, request.orderId(), request.orderAmount()
        );
        ApplyCouponResult result = couponCommandService.applyCoupon(command);
        return ResponseEntity.ok(ApplyCouponResponse.from(result));
    }
}
