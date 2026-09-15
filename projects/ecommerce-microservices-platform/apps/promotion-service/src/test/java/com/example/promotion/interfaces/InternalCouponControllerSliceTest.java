package com.example.promotion.interfaces;

import com.example.promotion.TestPromotionServiceApplication;
import com.example.promotion.application.service.CouponCommandService;
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

import static org.mockito.ArgumentMatchers.anyString;
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
