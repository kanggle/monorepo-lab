package com.example.promotion.interfaces.rest.controller;

import com.example.promotion.application.command.CreatePromotionCommand;
import com.example.promotion.application.command.IssueCouponsCommand;
import com.example.promotion.application.command.UpdatePromotionCommand;
import com.example.promotion.application.result.CreatePromotionResult;
import com.example.promotion.application.result.IssueCouponsResult;
import com.example.promotion.application.result.PromotionDetail;
import com.example.promotion.application.result.PromotionSummary;
import com.example.promotion.application.result.UpdatePromotionResult;
import com.example.promotion.application.service.CouponCommandService;
import com.example.promotion.application.service.PromotionCommandService;
import com.example.promotion.application.service.PromotionQueryService;
import com.example.common.page.PageResult;
import com.example.promotion.domain.promotion.PromotionStatus;
import com.example.promotion.interfaces.rest.dto.request.CreatePromotionRequest;
import com.example.promotion.interfaces.rest.dto.request.IssueCouponsRequest;
import com.example.promotion.interfaces.rest.dto.request.UpdatePromotionRequest;
import com.example.promotion.interfaces.rest.dto.response.CreatePromotionResponse;
import com.example.promotion.interfaces.rest.dto.response.IssueCouponsResponse;
import com.example.promotion.interfaces.rest.dto.response.PromotionDetailResponse;
import com.example.promotion.interfaces.rest.dto.response.PromotionListResponse;
import com.example.promotion.interfaces.rest.dto.response.UpdatePromotionResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/promotions")
public class PromotionController {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final PromotionCommandService promotionCommandService;
    private final PromotionQueryService promotionQueryService;
    private final CouponCommandService couponCommandService;

    @PostMapping
    public ResponseEntity<CreatePromotionResponse> createPromotion(
            @RequestHeader("X-User-Role") @NotBlank(message = "X-User-Role 헤더는 필수입니다") String role,
            @Valid @RequestBody CreatePromotionRequest request
    ) {
        CreatePromotionCommand command = new CreatePromotionCommand(
                request.name(), request.description(),
                request.discountType(), request.discountValue(),
                request.maxDiscountAmount(), request.maxIssuanceCount(),
                Instant.parse(request.startDate()), Instant.parse(request.endDate()),
                role
        );
        CreatePromotionResult result = promotionCommandService.createPromotion(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(CreatePromotionResponse.from(result));
    }

    @GetMapping
    public ResponseEntity<PromotionListResponse> getPromotions(
            @RequestHeader("X-User-Role") @NotBlank(message = "X-User-Role 헤더는 필수입니다") String role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = size < 1 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        PromotionStatus promotionStatus = PromotionControllerUtils.parsePromotionStatus(status);

        PageResult<PromotionSummary> result = promotionQueryService.getPromotions(safePage, safeSize, promotionStatus, role);
        return ResponseEntity.ok(PromotionListResponse.from(result));
    }

    @GetMapping("/{promotionId}")
    public ResponseEntity<PromotionDetailResponse> getPromotion(
            @RequestHeader("X-User-Role") @NotBlank(message = "X-User-Role 헤더는 필수입니다") String role,
            @PathVariable String promotionId
    ) {
        PromotionDetail detail = promotionQueryService.getPromotion(promotionId, role);
        return ResponseEntity.ok(PromotionDetailResponse.from(detail));
    }

    @PutMapping("/{promotionId}")
    public ResponseEntity<UpdatePromotionResponse> updatePromotion(
            @RequestHeader("X-User-Role") @NotBlank(message = "X-User-Role 헤더는 필수입니다") String role,
            @PathVariable String promotionId,
            @Valid @RequestBody UpdatePromotionRequest request
    ) {
        UpdatePromotionCommand command = new UpdatePromotionCommand(
                promotionId, request.name(), request.description(),
                request.discountType(), request.discountValue(),
                request.maxDiscountAmount(), request.maxIssuanceCount(),
                Instant.parse(request.startDate()), Instant.parse(request.endDate()),
                role
        );
        UpdatePromotionResult result = promotionCommandService.updatePromotion(command);
        return ResponseEntity.ok(UpdatePromotionResponse.from(result));
    }

    @DeleteMapping("/{promotionId}")
    public ResponseEntity<Void> deletePromotion(
            @RequestHeader("X-User-Role") @NotBlank(message = "X-User-Role 헤더는 필수입니다") String role,
            @PathVariable String promotionId
    ) {
        promotionCommandService.deletePromotion(promotionId, role);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{promotionId}/coupons/issue")
    public ResponseEntity<IssueCouponsResponse> issueCoupons(
            @RequestHeader("X-User-Role") @NotBlank(message = "X-User-Role 헤더는 필수입니다") String role,
            @PathVariable String promotionId,
            @Valid @RequestBody IssueCouponsRequest request
    ) {
        IssueCouponsCommand command = new IssueCouponsCommand(promotionId, request.userIds(), role);
        IssueCouponsResult result = couponCommandService.issueCoupons(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(IssueCouponsResponse.from(result));
    }
}
