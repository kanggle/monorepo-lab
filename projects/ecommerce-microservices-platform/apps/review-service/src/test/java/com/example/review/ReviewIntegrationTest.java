package com.example.review;

import com.example.review.application.port.PurchaseVerificationPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = ReviewServiceApplication.class)
@Tag("integration")
@Testcontainers
@AutoConfigureMockMvc
@DisplayName("리뷰 전체 흐름 통합 테스트")
class ReviewIntegrationTest {

    @SuppressWarnings("resource")
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("review_db")
            .withUsername("review_user")
            .withPassword("review_pass");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:0");
        registry.add("order-service.base-url", () -> "http://localhost:0");
    }

    @MockitoBean
    @SuppressWarnings("unused")
    private KafkaTemplate<String, String> kafkaTemplate;

    @MockitoBean
    private PurchaseVerificationPort purchaseVerificationPort;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("구매한 상품에 대해 리뷰를 생성하면 201을 반환한다")
    void createReview_validRequest_returns201() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        given(purchaseVerificationPort.hasUserPurchasedProduct(userId, productId)).willReturn(true);

        mockMvc.perform(post("/api/reviews")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createReviewJson(productId, "테스트상품", 5, "좋은 상품", "매우 만족합니다")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reviewId").isNotEmpty());
    }

    @Test
    @DisplayName("동일 사용자가 같은 상품에 중복 리뷰하면 409를 반환한다")
    void createReview_duplicate_returns409() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        given(purchaseVerificationPort.hasUserPurchasedProduct(userId, productId)).willReturn(true);

        mockMvc.perform(post("/api/reviews")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createReviewJson(productId, "테스트상품", 5, "좋은 상품", "매우 만족합니다")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/reviews")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createReviewJson(productId, "테스트상품", 4, "또 다른 리뷰", "중복 리뷰 시도")))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("미구매 상품에 대한 리뷰 작성 시 422를 반환한다")
    void createReview_notPurchased_returns422() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        given(purchaseVerificationPort.hasUserPurchasedProduct(userId, productId)).willReturn(false);

        mockMvc.perform(post("/api/reviews")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createReviewJson(productId, "테스트상품", 5, "좋은 상품", "매우 만족합니다")))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("리뷰 생성 후 상품별 리뷰 목록 조회가 가능하다")
    void getProductReviews_afterCreation_returnsList() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        given(purchaseVerificationPort.hasUserPurchasedProduct(userId, productId)).willReturn(true);

        mockMvc.perform(post("/api/reviews")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createReviewJson(productId, "테스트상품", 5, "좋은 상품", "매우 만족합니다")))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/reviews/products/{productId}", productId)
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].title").value("좋은 상품"))
                .andExpect(jsonPath("$.content[0].rating").value(5))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.averageRating").value(5.0));
    }

    @Test
    @DisplayName("자신의 리뷰를 수정하면 200을 반환한다")
    void updateReview_ownReview_returns200() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        given(purchaseVerificationPort.hasUserPurchasedProduct(userId, productId)).willReturn(true);

        MvcResult createResult = mockMvc.perform(post("/api/reviews")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createReviewJson(productId, "테스트상품", 5, "좋은 상품", "매우 만족합니다")))
                .andExpect(status().isCreated())
                .andReturn();

        String reviewId = extractReviewId(createResult);

        mockMvc.perform(put("/api/reviews/{reviewId}", reviewId)
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateReviewJson(3, "수정된 제목", "수정된 내용")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewId").value(reviewId));

        mockMvc.perform(get("/api/reviews/products/{productId}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("수정된 제목"))
                .andExpect(jsonPath("$.content[0].rating").value(3));
    }

    @Test
    @DisplayName("다른 사용자의 리뷰를 수정하면 403을 반환한다")
    void updateReview_otherUser_returns403() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        given(purchaseVerificationPort.hasUserPurchasedProduct(ownerId, productId)).willReturn(true);

        MvcResult createResult = mockMvc.perform(post("/api/reviews")
                        .header("X-User-Id", ownerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createReviewJson(productId, "테스트상품", 5, "좋은 상품", "매우 만족합니다")))
                .andExpect(status().isCreated())
                .andReturn();

        String reviewId = extractReviewId(createResult);

        mockMvc.perform(put("/api/reviews/{reviewId}", reviewId)
                        .header("X-User-Id", otherUserId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateReviewJson(3, "해킹 시도", "다른 사용자의 리뷰 수정")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("자신의 리뷰를 삭제하면 204를 반환하고 목록에서 제외된다")
    void deleteReview_ownReview_returns204AndExcludedFromList() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        given(purchaseVerificationPort.hasUserPurchasedProduct(userId, productId)).willReturn(true);

        MvcResult createResult = mockMvc.perform(post("/api/reviews")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createReviewJson(productId, "테스트상품", 5, "좋은 상품", "매우 만족합니다")))
                .andExpect(status().isCreated())
                .andReturn();

        String reviewId = extractReviewId(createResult);

        mockMvc.perform(delete("/api/reviews/{reviewId}", reviewId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/reviews/products/{productId}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("여러 리뷰 생성 후 상품별 평점 요약이 정확하게 계산된다")
    void getProductReviewSummary_multipleReviews_returnsCorrectSummary() throws Exception {
        UUID productId = UUID.randomUUID();

        int[] ratings = {5, 4, 3, 5, 4};
        for (int rating : ratings) {
            UUID userId = UUID.randomUUID();
            given(purchaseVerificationPort.hasUserPurchasedProduct(userId, productId)).willReturn(true);

            mockMvc.perform(post("/api/reviews")
                            .header("X-User-Id", userId.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createReviewJson(productId, "테스트상품", rating, "제목 " + rating, "내용 " + rating)))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(get("/api/reviews/products/{productId}/summary", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(productId.toString()))
                .andExpect(jsonPath("$.totalReviews").value(5))
                .andExpect(jsonPath("$.averageRating").value(4.2))
                .andExpect(jsonPath("$.ratingDistribution.5").value(2))
                .andExpect(jsonPath("$.ratingDistribution.4").value(2))
                .andExpect(jsonPath("$.ratingDistribution.3").value(1))
                .andExpect(jsonPath("$.ratingDistribution.2").value(0))
                .andExpect(jsonPath("$.ratingDistribution.1").value(0));
    }

    @Test
    @DisplayName("내 리뷰 목록 조회 시 자신의 리뷰만 반환된다")
    void getMyReviews_returnsOnlyOwnReviews() throws Exception {
        UUID myUserId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        UUID productId1 = UUID.randomUUID();
        UUID productId2 = UUID.randomUUID();
        UUID productId3 = UUID.randomUUID();

        given(purchaseVerificationPort.hasUserPurchasedProduct(any(), any())).willReturn(true);

        mockMvc.perform(post("/api/reviews")
                        .header("X-User-Id", myUserId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createReviewJson(productId1, "상품1", 5, "내 리뷰 1", "내용 1")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/reviews")
                        .header("X-User-Id", myUserId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createReviewJson(productId2, "상품2", 4, "내 리뷰 2", "내용 2")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/reviews")
                        .header("X-User-Id", otherUserId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createReviewJson(productId3, "상품3", 3, "다른 사람 리뷰", "내용 3")))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/reviews/me")
                        .header("X-User-Id", myUserId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @DisplayName("삭제된 리뷰와 동일 사용자+상품 조합으로 재리뷰를 작성할 수 있다")
    void createReview_afterDeletion_allowsReReview() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        given(purchaseVerificationPort.hasUserPurchasedProduct(userId, productId)).willReturn(true);

        MvcResult firstResult = mockMvc.perform(post("/api/reviews")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createReviewJson(productId, "테스트상품", 3, "첫 리뷰", "첫 번째 작성")))
                .andExpect(status().isCreated())
                .andReturn();

        String firstReviewId = extractReviewId(firstResult);

        mockMvc.perform(delete("/api/reviews/{reviewId}", firstReviewId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isNoContent());

        MvcResult secondResult = mockMvc.perform(post("/api/reviews")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createReviewJson(productId, "테스트상품", 5, "재리뷰", "재구매 후 다시 작성")))
                .andExpect(status().isCreated())
                .andReturn();

        String secondReviewId = extractReviewId(secondResult);
        assertThat(secondReviewId).isNotEqualTo(firstReviewId);

        mockMvc.perform(get("/api/reviews/products/{productId}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].title").value("재리뷰"))
                .andExpect(jsonPath("$.content[0].rating").value(5));
    }

    private String createReviewJson(UUID productId, String productName, int rating, String title, String content) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "productId", productId.toString(),
                "productName", productName,
                "rating", rating,
                "title", title,
                "content", content
        ));
    }

    private String updateReviewJson(int rating, String title, String content) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "rating", rating,
                "title", title,
                "content", content
        ));
    }

    @SuppressWarnings("unchecked")
    private String extractReviewId(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        Map<String, String> map = objectMapper.readValue(body, Map.class);
        return map.get("reviewId");
    }
}
