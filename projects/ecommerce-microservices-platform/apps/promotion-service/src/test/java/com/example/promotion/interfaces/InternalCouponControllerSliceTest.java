package com.example.promotion.interfaces;

import com.example.promotion.TestPromotionServiceApplication;
import com.example.promotion.application.service.CouponCommandService;
import com.example.promotion.application.service.StaleUsedCouponQueryService;
import com.example.promotion.domain.coupon.StaleUsedCoupon;
import com.example.promotion.interfaces.rest.controller.GlobalExceptionHandler;
import com.example.promotion.interfaces.rest.controller.InternalCouponController;
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

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {InternalCouponController.class, GlobalExceptionHandler.class})
@ContextConfiguration(classes = TestPromotionServiceApplication.class)
@DisplayName("InternalCouponController 슬라이스 테스트 (TASK-INT-026)")
class InternalCouponControllerSliceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CouponCommandService couponCommandService;

    @MockitoBean
    private StaleUsedCouponQueryService staleUsedCouponQueryService;

    @Test
    @DisplayName("stale-used — 본문이 없으면 기본값(60분, 200건)으로 조회하고 쿠폰의 테넌트를 싣는다 (TASK-INT-028)")
    void staleUsed_defaults_andCarriesTenant() throws Exception {
        given(staleUsedCouponQueryService.find(60, 200)).willReturn(List.of(
                new StaleUsedCoupon("coupon-1", "order-1", "tenant-b", Instant.parse("2026-09-16T01:00:00Z"))));

        mockMvc.perform(post("/api/internal/coupons/stale-used").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coupons[0].couponId").value("coupon-1"))
                .andExpect(jsonPath("$.coupons[0].orderId").value("order-1"))
                .andExpect(jsonPath("$.coupons[0].tenantId").value("tenant-b"));
    }

    @Test
    @DisplayName("stale-used — olderThanMinutes 가 30 미만이면 400 VALIDATION_ERROR 이고 조회하지 않는다")
    void staleUsed_belowFloor_returns400() throws Exception {
        mockMvc.perform(post("/api/internal/coupons/stale-used")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"olderThanMinutes\": 29}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verify(staleUsedCouponQueryService, never()).find(anyInt(), anyInt());
    }

    @Test
    @DisplayName("stale-used — limit 가 500 을 넘으면 400 VALIDATION_ERROR")
    void staleUsed_limitAboveMax_returns400() throws Exception {
        mockMvc.perform(post("/api/internal/coupons/stale-used")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"limit\": 501}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("해제 요청은 204 를 돌려주고 쿠폰 id 와 orderId 를 그대로 넘긴다")
    void release_returns204_andDelegates() throws Exception {
        mockMvc.perform(post("/api/internal/coupons/coupon-1/release")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\": \"order-1\"}"))
                .andExpect(status().isNoContent());

        verify(couponCommandService).releaseCoupon("coupon-1", "order-1");
    }

    @Test
    @DisplayName("orderId 가 비면 400 VALIDATION_ERROR 이고 해제하지 않는다")
    void release_blankOrderId_returns400() throws Exception {
        mockMvc.perform(post("/api/internal/coupons/coupon-1/release")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verify(couponCommandService, never()).releaseCoupon(anyString(), anyString());
    }
}
