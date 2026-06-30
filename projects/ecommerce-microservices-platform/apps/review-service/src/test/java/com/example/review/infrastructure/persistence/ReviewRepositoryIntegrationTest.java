package com.example.review.infrastructure.persistence;

import com.example.review.application.port.PurchaseVerificationPort;
import com.example.review.application.port.ReviewQueryPort;
import com.example.review.application.result.ReviewListResult;
import com.example.review.application.result.ReviewSummaryResult;
import com.example.review.application.result.MyReviewListResult;
import com.example.review.domain.model.Review;
import com.example.review.domain.model.ReviewStatus;
import com.example.review.ReviewServiceApplication;
import com.example.review.domain.repository.ReviewRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = ReviewServiceApplication.class)
@Tag("integration")
@Testcontainers
@Transactional
@DisplayName("ReviewRepository 통합 테스트")
class ReviewRepositoryIntegrationTest {

    private static final Clock TEST_CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

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
    @SuppressWarnings("unused")
    private PurchaseVerificationPort purchaseVerificationPort;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private ReviewQueryPort reviewQueryPort;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("리뷰를 저장하고 조회할 수 있다")
    void save_andFindById_success() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Review review = Review.create(userId, productId, "테스트상품", 5, "좋은 상품", "매우 만족합니다", TEST_CLOCK);

        reviewRepository.save(review);

        Optional<Review> found = reviewRepository.findActiveById(review.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getUserId()).isEqualTo(userId);
        assertThat(found.get().getProductId()).isEqualTo(productId);
        assertThat(found.get().getRatingValue()).isEqualTo(5);
        assertThat(found.get().getTitle()).isEqualTo("좋은 상품");
    }

    @Test
    @DisplayName("중복 리뷰 존재 여부를 확인할 수 있다")
    void existsByUserIdAndProductId_success() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Review review = Review.create(userId, productId, "테스트상품", 5, "좋은 상품", "매우 만족합니다", TEST_CLOCK);

        reviewRepository.save(review);

        assertThat(reviewRepository.existsByUserIdAndProductId(userId, productId)).isTrue();
        assertThat(reviewRepository.existsByUserIdAndProductId(userId, UUID.randomUUID())).isFalse();
    }

    @Test
    @DisplayName("삭제된 리뷰는 활성 상태 조회에서 제외된다")
    void findActiveById_deletedReview_empty() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Review review = Review.create(userId, productId, "테스트상품", 5, "좋은 상품", "매우 만족합니다", TEST_CLOCK);
        reviewRepository.save(review);

        review.softDelete(TEST_CLOCK);
        reviewRepository.save(review);

        Optional<Review> found = reviewRepository.findActiveById(review.getId());
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("리뷰를 수정하고 저장할 수 있다")
    void update_andSave_success() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Review review = Review.create(userId, productId, "테스트상품", 5, "좋은 상품", "매우 만족합니다", TEST_CLOCK);
        reviewRepository.save(review);

        Review loaded = reviewRepository.findActiveById(review.getId()).orElseThrow();
        loaded.update(3, "수정된 제목", "수정된 내용", TEST_CLOCK);
        reviewRepository.save(loaded);

        Review updated = reviewRepository.findActiveById(review.getId()).orElseThrow();
        assertThat(updated.getRatingValue()).isEqualTo(3);
        assertThat(updated.getTitle()).isEqualTo("수정된 제목");
        assertThat(updated.getContent()).isEqualTo("수정된 내용");
    }

    @Test
    @DisplayName("상품별 리뷰 목록을 페이지네이션으로 조회할 수 있다")
    void findByProductId_pagination_success() {
        UUID productId = UUID.randomUUID();
        for (int i = 0; i < 3; i++) {
            Review review = Review.create(UUID.randomUUID(), productId, "상품" + i, i + 3, "제목 " + i, "내용 " + i, TEST_CLOCK);
            reviewRepository.save(review);
        }

        ReviewListResult result = reviewQueryPort.findByProductId(productId, 0, 2, "createdAt,desc");

        assertThat(result.content()).hasSize(2);
        assertThat(result.totalElements()).isEqualTo(3);
        assertThat(result.totalReviews()).isEqualTo(3);
        assertThat(result.averageRating()).isGreaterThan(0);
    }

    @Test
    @DisplayName("상품별 평점 요약을 조회할 수 있다")
    void getSummaryByProductId_success() {
        UUID productId = UUID.randomUUID();
        reviewRepository.save(Review.create(UUID.randomUUID(), productId, "상품A", 5, "제목1", "내용1", TEST_CLOCK));
        reviewRepository.save(Review.create(UUID.randomUUID(), productId, "상품A", 4, "제목2", "내용2", TEST_CLOCK));
        reviewRepository.save(Review.create(UUID.randomUUID(), productId, "상품A", 3, "제목3", "내용3", TEST_CLOCK));

        ReviewSummaryResult summary = reviewQueryPort.getSummaryByProductId(productId);

        assertThat(summary.productId()).isEqualTo(productId);
        assertThat(summary.totalReviews()).isEqualTo(3);
        assertThat(summary.averageRating()).isEqualTo(4.0);
        assertThat(summary.ratingDistribution().get(5)).isEqualTo(1L);
        assertThat(summary.ratingDistribution().get(4)).isEqualTo(1L);
        assertThat(summary.ratingDistribution().get(3)).isEqualTo(1L);
        assertThat(summary.ratingDistribution().get(2)).isEqualTo(0L);
        assertThat(summary.ratingDistribution().get(1)).isEqualTo(0L);
    }

    @Test
    @DisplayName("소프트 삭제 후 동일 사용자+상품 조합으로 재리뷰를 저장할 수 있다")
    void saveReview_afterSoftDeleteSameUserAndProduct_succeeds() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        Review firstReview = Review.create(userId, productId, "테스트상품", 4, "첫 리뷰", "첫 번째 리뷰입니다", TEST_CLOCK);
        reviewRepository.save(firstReview);

        firstReview.softDelete(TEST_CLOCK);
        reviewRepository.save(firstReview);
        entityManager.flush();
        entityManager.clear();

        Review secondReview = Review.create(userId, productId, "테스트상품", 5, "재리뷰", "재구매 후 다시 작성합니다", TEST_CLOCK);
        Review saved = reviewRepository.save(secondReview);

        assertThat(saved.getId()).isNotEqualTo(firstReview.getId());
        assertThat(saved.getStatus()).isEqualTo(ReviewStatus.ACTIVE);

        Optional<Review> found = reviewRepository.findActiveById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getUserId()).isEqualTo(userId);
        assertThat(found.get().getProductId()).isEqualTo(productId);
        assertThat(found.get().getTitle()).isEqualTo("재리뷰");
    }

    @Test
    @DisplayName("소프트 삭제 후 existsByUserIdAndProductId가 false를 반환한다")
    void existsByUserIdAndProductId_afterSoftDelete_returnsFalse() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        Review review = Review.create(userId, productId, "테스트상품", 5, "좋은 상품", "매우 만족합니다", TEST_CLOCK);
        reviewRepository.save(review);
        assertThat(reviewRepository.existsByUserIdAndProductId(userId, productId)).isTrue();

        review.softDelete(TEST_CLOCK);
        reviewRepository.save(review);

        assertThat(reviewRepository.existsByUserIdAndProductId(userId, productId)).isFalse();
    }

    @Test
    @DisplayName("사용자별 리뷰 목록을 조회할 수 있다")
    void findByUserId_success() {
        UUID userId = UUID.randomUUID();
        reviewRepository.save(Review.create(userId, UUID.randomUUID(), "상품1", 5, "제목1", "내용1", TEST_CLOCK));
        reviewRepository.save(Review.create(userId, UUID.randomUUID(), "상품2", 4, "제목2", "내용2", TEST_CLOCK));

        MyReviewListResult result = reviewQueryPort.findByUserId(userId, 0, 20);

        assertThat(result.content()).hasSize(2);
        assertThat(result.totalElements()).isEqualTo(2);
    }
}
