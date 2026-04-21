package com.example.review.application.service;

import com.example.review.application.port.ReviewQueryPort;
import com.example.review.application.result.MyReviewListResult;
import com.example.review.application.result.ReviewListResult;
import com.example.review.application.result.ReviewSummaryResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReviewQueryService 단위 테스트")
class ReviewQueryServiceTest {

    @InjectMocks
    private ReviewQueryService reviewQueryService;

    @Mock
    private ReviewQueryPort reviewQueryPort;

    @Test
    @DisplayName("상품별 리뷰 목록을 조회할 수 있다")
    void getProductReviews_success() {
        UUID productId = UUID.randomUUID();
        ReviewListResult expected = new ReviewListResult(
                List.of(new ReviewListResult.ReviewItem(
                        UUID.randomUUID(), UUID.randomUUID(), 5, "좋은 상품", "만족", Instant.now(), Instant.now())),
                0, 20, 1, 5.0, 1
        );

        given(reviewQueryPort.findByProductId(productId, 0, 20, "createdAt,desc")).willReturn(expected);

        ReviewListResult result = reviewQueryService.getProductReviews(productId, 0, 20, "createdAt,desc");

        assertThat(result.content()).hasSize(1);
        assertThat(result.averageRating()).isEqualTo(5.0);
    }

    @Test
    @DisplayName("상품 평점 요약을 조회할 수 있다")
    void getProductReviewSummary_success() {
        UUID productId = UUID.randomUUID();
        ReviewSummaryResult expected = new ReviewSummaryResult(
                productId, 4.3, 15,
                Map.of(1, 1L, 2, 0L, 3, 2L, 4, 5L, 5, 7L)
        );

        given(reviewQueryPort.getSummaryByProductId(productId)).willReturn(expected);

        ReviewSummaryResult result = reviewQueryService.getProductReviewSummary(productId);

        assertThat(result.averageRating()).isEqualTo(4.3);
        assertThat(result.totalReviews()).isEqualTo(15);
    }

    @Test
    @DisplayName("내 리뷰 목록을 조회할 수 있다")
    void getMyReviews_success() {
        UUID userId = UUID.randomUUID();
        MyReviewListResult expected = new MyReviewListResult(
                List.of(new MyReviewListResult.MyReviewItem(
                        UUID.randomUUID(), UUID.randomUUID(), "테스트상품", 5, "제목", "내용", Instant.now())),
                0, 20, 1
        );

        given(reviewQueryPort.findByUserId(userId, 0, 20)).willReturn(expected);

        MyReviewListResult result = reviewQueryService.getMyReviews(userId, 0, 20);

        assertThat(result.content()).hasSize(1);
    }
}
