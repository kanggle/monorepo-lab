package com.example.review.interfaces.controller;

import com.example.review.TestReviewServiceApplication;
import com.example.review.application.result.CreateReviewResult;
import com.example.review.application.result.MyReviewListResult;
import com.example.review.application.result.ReviewListResult;
import com.example.review.application.result.ReviewSummaryResult;
import com.example.review.application.result.UpdateReviewResult;
import com.example.review.application.service.ReviewCommandService;
import com.example.review.application.service.ReviewQueryService;
import com.example.review.domain.exception.ProductNotPurchasedException;
import com.example.review.domain.exception.ReviewAccessDeniedException;
import com.example.review.domain.exception.ReviewAlreadyExistsException;
import com.example.review.domain.exception.ReviewNotFoundException;
import com.example.review.interfaces.advice.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ReviewController.class)
@ContextConfiguration(classes = TestReviewServiceApplication.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("ReviewController 슬라이스 테스트")
class ReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReviewCommandService reviewCommandService;

    @MockitoBean
    private ReviewQueryService reviewQueryService;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final UUID REVIEW_ID = UUID.randomUUID();

    @Test
    @DisplayName("POST /api/reviews - 리뷰 작성 성공 시 201 반환")
    void createReview_success_returns201() throws Exception {
        given(reviewCommandService.createReview(any()))
                .willReturn(new CreateReviewResult(REVIEW_ID));

        mockMvc.perform(post("/api/reviews")
                        .header("X-User-Id", USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "productId": "%s",
                                    "rating": 5,
                                    "title": "Good product",
                                    "content": "Very satisfied"
                                }
                                """.formatted(PRODUCT_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reviewId").value(REVIEW_ID.toString()));
    }

    @Test
    @DisplayName("POST /api/reviews - 유효하지 않은 요청 시 400 반환")
    void createReview_invalidRequest_returns400() throws Exception {
        mockMvc.perform(post("/api/reviews")
                        .header("X-User-Id", USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "productId": "%s",
                                    "rating": 0,
                                    "title": "",
                                    "content": ""
                                }
                                """.formatted(PRODUCT_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST /api/reviews - X-User-Id 헤더 누락 시 401 반환")
    void createReview_missingHeader_returns401() throws Exception {
        mockMvc.perform(post("/api/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "productId": "%s",
                                    "rating": 5,
                                    "title": "Good",
                                    "content": "Content"
                                }
                                """.formatted(PRODUCT_ID)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("POST /api/reviews - 중복 리뷰 시 409 반환")
    void createReview_alreadyExists_returns409() throws Exception {
        given(reviewCommandService.createReview(any()))
                .willThrow(new ReviewAlreadyExistsException(USER_ID, PRODUCT_ID));

        mockMvc.perform(post("/api/reviews")
                        .header("X-User-Id", USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "productId": "%s",
                                    "rating": 5,
                                    "title": "Good",
                                    "content": "Content"
                                }
                                """.formatted(PRODUCT_ID)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVIEW_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("POST /api/reviews - 미구매 상품 시 422 반환")
    void createReview_notPurchased_returns422() throws Exception {
        given(reviewCommandService.createReview(any()))
                .willThrow(new ProductNotPurchasedException(USER_ID, PRODUCT_ID));

        mockMvc.perform(post("/api/reviews")
                        .header("X-User-Id", USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "productId": "%s",
                                    "rating": 5,
                                    "title": "Good",
                                    "content": "Content"
                                }
                                """.formatted(PRODUCT_ID)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_PURCHASED"));
    }

    @Test
    @DisplayName("GET /api/reviews/products/{productId} - 상품별 리뷰 목록 조회 성공")
    void getProductReviews_success_returns200() throws Exception {
        ReviewListResult result = new ReviewListResult(
                List.of(new ReviewListResult.ReviewItem(
                        REVIEW_ID, USER_ID, 5, "Good", "Content", Instant.now(), Instant.now())),
                0, 20, 1, 5.0, 1
        );
        given(reviewQueryService.getProductReviews(eq(PRODUCT_ID), anyInt(), anyInt(), anyString()))
                .willReturn(result);

        mockMvc.perform(get("/api/reviews/products/{productId}", PRODUCT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].reviewId").value(REVIEW_ID.toString()))
                .andExpect(jsonPath("$.averageRating").value(5.0))
                .andExpect(jsonPath("$.totalReviews").value(1));
    }

    @Test
    @DisplayName("GET /api/reviews/products/{productId}/summary - 평점 요약 조회 성공")
    void getProductReviewSummary_success_returns200() throws Exception {
        ReviewSummaryResult result = new ReviewSummaryResult(
                PRODUCT_ID, 4.3, 15,
                Map.of(1, 1L, 2, 0L, 3, 2L, 4, 5L, 5, 7L)
        );
        given(reviewQueryService.getProductReviewSummary(PRODUCT_ID)).willReturn(result);

        mockMvc.perform(get("/api/reviews/products/{productId}/summary", PRODUCT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(PRODUCT_ID.toString()))
                .andExpect(jsonPath("$.averageRating").value(4.3))
                .andExpect(jsonPath("$.totalReviews").value(15));
    }

    @Test
    @DisplayName("GET /api/reviews/me - 내 리뷰 목록 조회 성공")
    void getMyReviews_success_returns200() throws Exception {
        MyReviewListResult result = new MyReviewListResult(
                List.of(new MyReviewListResult.MyReviewItem(
                        REVIEW_ID, PRODUCT_ID, "테스트상품", 5, "Good", "Content", Instant.now())),
                0, 20, 1
        );
        given(reviewQueryService.getMyReviews(eq(USER_ID), anyInt(), anyInt())).willReturn(result);

        mockMvc.perform(get("/api/reviews/me")
                        .header("X-User-Id", USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("PUT /api/reviews/{reviewId} - 리뷰 수정 성공")
    void updateReview_success_returns200() throws Exception {
        given(reviewCommandService.updateReview(any()))
                .willReturn(new UpdateReviewResult(REVIEW_ID));

        mockMvc.perform(put("/api/reviews/{reviewId}", REVIEW_ID)
                        .header("X-User-Id", USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "rating": 4,
                                    "title": "Updated",
                                    "content": "Updated content"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewId").value(REVIEW_ID.toString()));
    }

    @Test
    @DisplayName("PUT /api/reviews/{reviewId} - 다른 사용자의 리뷰 수정 시 403 반환")
    void updateReview_notOwner_returns403() throws Exception {
        given(reviewCommandService.updateReview(any()))
                .willThrow(new ReviewAccessDeniedException(USER_ID, REVIEW_ID));

        mockMvc.perform(put("/api/reviews/{reviewId}", REVIEW_ID)
                        .header("X-User-Id", USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "rating": 4,
                                    "title": "Updated",
                                    "content": "Updated content"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("PUT /api/reviews/{reviewId} - 존재하지 않는 리뷰 수정 시 404 반환")
    void updateReview_notFound_returns404() throws Exception {
        given(reviewCommandService.updateReview(any()))
                .willThrow(new ReviewNotFoundException(REVIEW_ID));

        mockMvc.perform(put("/api/reviews/{reviewId}", REVIEW_ID)
                        .header("X-User-Id", USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "rating": 4,
                                    "title": "Updated",
                                    "content": "Updated content"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REVIEW_NOT_FOUND"));
    }

    @Test
    @DisplayName("DELETE /api/reviews/{reviewId} - 리뷰 삭제 성공 시 204 반환")
    void deleteReview_success_returns204() throws Exception {
        willDoNothing().given(reviewCommandService).deleteReview(USER_ID, REVIEW_ID);

        mockMvc.perform(delete("/api/reviews/{reviewId}", REVIEW_ID)
                        .header("X-User-Id", USER_ID.toString()))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /api/reviews/{reviewId} - 다른 사용자의 리뷰 삭제 시 403 반환")
    void deleteReview_notOwner_returns403() throws Exception {
        willThrow(new ReviewAccessDeniedException(USER_ID, REVIEW_ID))
                .given(reviewCommandService).deleteReview(USER_ID, REVIEW_ID);

        mockMvc.perform(delete("/api/reviews/{reviewId}", REVIEW_ID)
                        .header("X-User-Id", USER_ID.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("GET /api/reviews/products/{productId} - 허용되지 않은 sort 필드 시 400 반환")
    void getProductReviews_invalidSortField_returns400() throws Exception {
        mockMvc.perform(get("/api/reviews/products/{productId}", PRODUCT_ID)
                        .param("sort", "invalidField,desc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REVIEW_REQUEST"));
    }

}
