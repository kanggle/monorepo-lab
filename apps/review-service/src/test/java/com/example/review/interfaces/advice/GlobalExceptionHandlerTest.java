package com.example.review.interfaces.advice;

import com.example.review.TestReviewServiceApplication;
import com.example.review.application.service.ReviewCommandService;
import com.example.review.application.service.ReviewQueryService;
import com.example.review.domain.exception.ProductNotPurchasedException;
import com.example.review.domain.exception.ReviewAccessDeniedException;
import com.example.review.domain.exception.ReviewAlreadyExistsException;
import com.example.review.domain.exception.ReviewNotFoundException;
import com.example.review.interfaces.controller.ReviewController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ReviewController.class)
@ContextConfiguration(classes = TestReviewServiceApplication.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("GlobalExceptionHandler 슬라이스 테스트")
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReviewCommandService reviewCommandService;

    @MockitoBean
    private ReviewQueryService reviewQueryService;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final UUID REVIEW_ID = UUID.randomUUID();

    // -----------------------------------------------------------------------
    // 400 Bad Request — MethodArgumentNotValidException
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("MethodArgumentNotValidException 발생 시 400 / VALIDATION_ERROR 반환")
    void handleValidation_returns400() throws Exception {
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
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    // -----------------------------------------------------------------------
    // 400 Bad Request — HttpMessageNotReadableException (UUID 파싱 실패, 깨진 JSON)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("productId가 UUID 형식이 아니면 400 / VALIDATION_ERROR 반환")
    void handleNonUuidBody_returns400() throws Exception {
        mockMvc.perform(post("/api/reviews")
                        .header("X-User-Id", USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "productId": "mock-1",
                                    "rating": 5,
                                    "title": "Good",
                                    "content": "Content"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Malformed request body"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("요청 본문이 깨진 JSON이면 400 / VALIDATION_ERROR 반환")
    void handleMalformedJson_returns400() throws Exception {
        mockMvc.perform(post("/api/reviews")
                        .header("X-User-Id", USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Malformed request body"));
    }

    // -----------------------------------------------------------------------
    // 400 Bad Request — ConstraintViolationException (@NotBlank on @RequestHeader)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("ConstraintViolationException 발생 시 400 / VALIDATION_ERROR 반환")
    void handleConstraintViolation_returns400() throws Exception {
        // @RequestHeader + @NotBlank: 빈 문자열 전송 시 ConstraintViolationException 발생
        mockMvc.perform(post("/api/reviews")
                        .header("X-User-Id", "")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "productId": "%s",
                                    "rating": 5,
                                    "title": "Good",
                                    "content": "Content"
                                }
                                """.formatted(PRODUCT_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    // -----------------------------------------------------------------------
    // 401 Unauthorized — MissingRequestHeaderException
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("X-User-Id 헤더 누락 시 401 / UNAUTHORIZED 반환")
    void handleMissingHeader_returns401() throws Exception {
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
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    // -----------------------------------------------------------------------
    // 403 Forbidden — ReviewAccessDeniedException
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("ReviewAccessDeniedException 발생 시 403 / ACCESS_DENIED 반환")
    void handleAccessDenied_returns403() throws Exception {
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
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    // -----------------------------------------------------------------------
    // 404 Not Found — ReviewNotFoundException
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("ReviewNotFoundException 발생 시 404 / REVIEW_NOT_FOUND 반환")
    void handleReviewNotFound_returns404() throws Exception {
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
                .andExpect(jsonPath("$.code").value("REVIEW_NOT_FOUND"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    // -----------------------------------------------------------------------
    // 409 Conflict — ReviewAlreadyExistsException
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("ReviewAlreadyExistsException 발생 시 409 / REVIEW_ALREADY_EXISTS 반환")
    void handleReviewAlreadyExists_returns409() throws Exception {
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
                .andExpect(jsonPath("$.code").value("REVIEW_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    // -----------------------------------------------------------------------
    // 422 Unprocessable Entity — ProductNotPurchasedException
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("ProductNotPurchasedException 발생 시 422 / PRODUCT_NOT_PURCHASED 반환")
    void handleProductNotPurchased_returns422() throws Exception {
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
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_PURCHASED"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    // -----------------------------------------------------------------------
    // 500 Internal Server Error — unexpected Exception
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("예상치 못한 Exception 발생 시 500 / INTERNAL_ERROR 반환")
    void handleUnexpected_returns500() throws Exception {
        given(reviewCommandService.createReview(any()))
                .willThrow(new RuntimeException("Unexpected failure"));

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
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }
}
