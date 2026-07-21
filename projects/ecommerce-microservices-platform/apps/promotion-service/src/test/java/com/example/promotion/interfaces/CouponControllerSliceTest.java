package com.example.promotion.interfaces;

import com.example.promotion.TestPromotionServiceApplication;
import com.example.promotion.application.result.ApplyCouponResult;
import com.example.promotion.application.result.CouponDetail;
import com.example.promotion.application.service.CouponCommandService;
import com.example.promotion.application.service.CouponQueryService;
import com.example.promotion.domain.coupon.CouponAlreadyUsedException;
import com.example.promotion.domain.coupon.CouponNotFoundException;
import com.example.promotion.domain.coupon.CouponStatus;
import com.example.promotion.domain.promotion.DiscountType;
import com.example.common.page.PageResult;
import com.example.promotion.interfaces.rest.controller.CouponController;
import com.example.promotion.interfaces.rest.controller.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {CouponController.class, GlobalExceptionHandler.class})
@ContextConfiguration(classes = TestPromotionServiceApplication.class)
@DisplayName("CouponController 슬라이스 테스트")
class CouponControllerSliceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CouponCommandService couponCommandService;

    @MockitoBean
    private CouponQueryService couponQueryService;

    @Test
    @DisplayName("내 쿠폰 목록 조회 시 200이 반환된다")
    void getMyCoupons_validRequest_returns200() throws Exception {
        CouponDetail detail = new CouponDetail(
                "coupon-1", "promo-1", "봄할인",
                DiscountType.FIXED, 5000, 10000,
                CouponStatus.ISSUED,
                Instant.parse("2026-03-15T00:00:00Z"),
                Instant.parse("2026-04-01T00:00:00Z")
        );
        given(couponQueryService.getMyCoupons(eq("user-1"), anyInt(), anyInt(), any()))
                .willReturn(new PageResult<>(List.of(detail), 0, 20, 1L, 1));

        mockMvc.perform(get("/api/coupons/me")
                        .header("X-User-Id", "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].couponId").value("coupon-1"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("쿠폰 적용 시 200이 반환된다")
    void applyCoupon_validRequest_returns200() throws Exception {
        given(couponCommandService.applyCoupon(any()))
                .willReturn(new ApplyCouponResult("coupon-1", 5000, 25000));

        mockMvc.perform(post("/api/coupons/coupon-1/apply")
                        .header("X-User-Id", "user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderId": "order-1",
                                  "orderAmount": 30000
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.couponId").value("coupon-1"))
                .andExpect(jsonPath("$.discountAmount").value(5000))
                .andExpect(jsonPath("$.finalAmount").value(25000));
    }

    @Test
    @DisplayName("존재하지 않는 쿠폰 적용 시 404가 반환된다")
    void applyCoupon_notFound_returns404() throws Exception {
        given(couponCommandService.applyCoupon(any()))
                .willThrow(new CouponNotFoundException("non-existent"));

        mockMvc.perform(post("/api/coupons/non-existent/apply")
                        .header("X-User-Id", "user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderId": "order-1",
                                  "orderAmount": 30000
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COUPON_NOT_FOUND"));
    }

    @Test
    @DisplayName("이미 사용된 쿠폰 적용 시 422가 반환된다")
    void applyCoupon_alreadyUsed_returns422() throws Exception {
        given(couponCommandService.applyCoupon(any()))
                .willThrow(new CouponAlreadyUsedException("coupon-1"));

        mockMvc.perform(post("/api/coupons/coupon-1/apply")
                        .header("X-User-Id", "user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderId": "order-1",
                                  "orderAmount": 30000
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("COUPON_ALREADY_USED"));
    }

    @Test
    @DisplayName("X-User-Id 헤더 누락 시 401이 반환된다")
    void getMyCoupons_missingHeader_returns401() throws Exception {
        mockMvc.perform(get("/api/coupons/me"))
                .andExpect(status().isUnauthorized());
    }
}
