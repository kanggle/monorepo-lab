package com.example.promotion.interfaces;

import com.example.promotion.TestPromotionServiceApplication;
import com.example.web.exception.AccessDeniedException;
import com.example.promotion.application.result.CreatePromotionResult;
import com.example.promotion.application.result.PromotionDetail;
import com.example.promotion.application.result.PromotionSummary;
import com.example.promotion.application.service.CouponCommandService;
import com.example.promotion.application.service.PromotionCommandService;
import com.example.promotion.application.service.PromotionQueryService;
import com.example.promotion.domain.promotion.DiscountType;
import com.example.common.page.PageResult;
import com.example.promotion.domain.promotion.PromotionNotFoundException;
import com.example.promotion.domain.promotion.PromotionStatus;
import com.example.promotion.interfaces.rest.controller.GlobalExceptionHandler;
import com.example.promotion.interfaces.rest.controller.PromotionController;
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
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {PromotionController.class, GlobalExceptionHandler.class})
@ContextConfiguration(classes = TestPromotionServiceApplication.class)
@DisplayName("PromotionController 슬라이스 테스트")
class PromotionControllerSliceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PromotionCommandService promotionCommandService;

    @MockitoBean
    private PromotionQueryService promotionQueryService;

    @MockitoBean
    private CouponCommandService couponCommandService;

    @Test
    @DisplayName("프로모션 생성 요청 시 201이 반환된다")
    void createPromotion_validRequest_returns201() throws Exception {
        given(promotionCommandService.createPromotion(any()))
                .willReturn(new CreatePromotionResult("promo-123"));

        mockMvc.perform(post("/api/promotions")
                        .header("X-User-Role", "ECOMMERCE_OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "봄맞이 할인",
                                  "description": "봄 시즌 프로모션",
                                  "discountType": "FIXED",
                                  "discountValue": 5000,
                                  "maxDiscountAmount": 10000,
                                  "maxIssuanceCount": 100,
                                  "startDate": "2026-03-01T00:00:00Z",
                                  "endDate": "2026-04-01T00:00:00Z"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.promotionId").value("promo-123"));
    }

    @Test
    @DisplayName("깨진 JSON 본문이면 400 / VALIDATION_ERROR 반환")
    void createPromotion_malformedBody_returns400() throws Exception {
        mockMvc.perform(post("/api/promotions")
                        .header("X-User-Role", "ECOMMERCE_OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Malformed request body"));
    }

    @Test
    @DisplayName("ECOMMERCE_OPERATOR 아닌 역할로 요청 시 403이 반환된다")
    void createPromotion_nonAdminRole_returns403() throws Exception {
        given(promotionCommandService.createPromotion(any()))
                .willThrow(new AccessDeniedException());

        mockMvc.perform(post("/api/promotions")
                        .header("X-User-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "할인",
                                  "discountType": "FIXED",
                                  "discountValue": 5000,
                                  "maxIssuanceCount": 100,
                                  "startDate": "2026-03-01T00:00:00Z",
                                  "endDate": "2026-04-01T00:00:00Z"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("프로모션 목록 조회 시 200이 반환된다")
    void getPromotions_validRequest_returns200() throws Exception {
        PromotionSummary summary = new PromotionSummary(
                "promo-1", "할인", DiscountType.FIXED, 5000, 100, 50,
                Instant.parse("2026-03-01T00:00:00Z"),
                Instant.parse("2026-04-01T00:00:00Z"),
                PromotionStatus.ACTIVE
        );
        given(promotionQueryService.getPromotions(0, 20, null, "ECOMMERCE_OPERATOR"))
                .willReturn(new PageResult<>(List.of(summary), 0, 20, 1L, 1));

        mockMvc.perform(get("/api/promotions")
                        .header("X-User-Role", "ECOMMERCE_OPERATOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].promotionId").value("promo-1"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("프로모션 상세 조회 시 200이 반환된다")
    void getPromotion_validRequest_returns200() throws Exception {
        PromotionDetail detail = new PromotionDetail(
                "promo-1", "할인", "설명", DiscountType.FIXED, 5000, 10000,
                100, 50,
                Instant.parse("2026-03-01T00:00:00Z"),
                Instant.parse("2026-04-01T00:00:00Z"),
                PromotionStatus.ACTIVE,
                Instant.parse("2026-02-28T00:00:00Z"),
                Instant.parse("2026-02-28T00:00:00Z")
        );
        given(promotionQueryService.getPromotion("promo-1", "ECOMMERCE_OPERATOR")).willReturn(detail);

        mockMvc.perform(get("/api/promotions/promo-1")
                        .header("X-User-Role", "ECOMMERCE_OPERATOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.promotionId").value("promo-1"))
                .andExpect(jsonPath("$.name").value("할인"));
    }

    @Test
    @DisplayName("존재하지 않는 프로모션 조회 시 404가 반환된다")
    void getPromotion_notFound_returns404() throws Exception {
        given(promotionQueryService.getPromotion("non-existent", "ECOMMERCE_OPERATOR"))
                .willThrow(new PromotionNotFoundException("non-existent"));

        mockMvc.perform(get("/api/promotions/non-existent")
                        .header("X-User-Role", "ECOMMERCE_OPERATOR"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROMOTION_NOT_FOUND"));
    }

    @Test
    @DisplayName("프로모션 삭제 시 204가 반환된다")
    void deletePromotion_validRequest_returns204() throws Exception {
        mockMvc.perform(delete("/api/promotions/promo-1")
                        .header("X-User-Role", "ECOMMERCE_OPERATOR"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("필수 필드 누락 시 400이 반환된다")
    void createPromotion_missingName_returns400() throws Exception {
        mockMvc.perform(post("/api/promotions")
                        .header("X-User-Role", "ECOMMERCE_OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "discountType": "FIXED",
                                  "discountValue": 5000,
                                  "maxIssuanceCount": 100,
                                  "startDate": "2026-03-01T00:00:00Z",
                                  "endDate": "2026-04-01T00:00:00Z"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
