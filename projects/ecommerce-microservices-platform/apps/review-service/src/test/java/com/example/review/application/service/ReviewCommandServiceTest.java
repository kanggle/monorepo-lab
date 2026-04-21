package com.example.review.application.service;

import com.example.review.application.command.CreateReviewCommand;
import com.example.review.application.command.UpdateReviewCommand;
import com.example.review.application.port.PurchaseVerificationPort;
import com.example.review.application.result.CreateReviewResult;
import com.example.review.application.result.UpdateReviewResult;
import com.example.review.domain.event.ReviewEventPublisher;
import com.example.review.domain.exception.ProductNotPurchasedException;
import com.example.review.domain.exception.ReviewAccessDeniedException;
import com.example.review.domain.exception.ReviewAlreadyExistsException;
import com.example.review.domain.exception.ReviewNotFoundException;
import com.example.review.domain.model.Review;
import com.example.review.domain.model.ReviewStatus;
import com.example.review.domain.repository.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReviewCommandService 단위 테스트")
class ReviewCommandServiceTest {

    private ReviewCommandService reviewCommandService;

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private PurchaseVerificationPort purchaseVerificationPort;

    @Mock
    private ReviewEventPublisher reviewEventPublisher;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final Instant FIXED_TIME = Instant.parse("2026-01-01T00:00:00Z");
    private final Clock fixedClock = Clock.fixed(FIXED_TIME, ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        reviewCommandService = new ReviewCommandService(
                reviewRepository, purchaseVerificationPort, reviewEventPublisher, fixedClock);
    }

    @Test
    @DisplayName("구매한 상품에 대해 리뷰를 작성할 수 있다")
    void createReview_validPurchase_success() {
        CreateReviewCommand command = new CreateReviewCommand(USER_ID, PRODUCT_ID, "테스트상품", 5, "좋은 상품", "매우 만족합니다");

        given(reviewRepository.existsByUserIdAndProductId(USER_ID, PRODUCT_ID)).willReturn(false);
        given(purchaseVerificationPort.hasUserPurchasedProduct(USER_ID, PRODUCT_ID)).willReturn(true);
        given(reviewRepository.save(any(Review.class))).willAnswer(inv -> inv.getArgument(0));

        CreateReviewResult result = reviewCommandService.createReview(command);

        assertThat(result.reviewId()).isNotNull();
        verify(reviewRepository).save(any(Review.class));
        verify(reviewEventPublisher).publish(any());
    }

    @Test
    @DisplayName("리뷰 생성 시 createdAt이 고정 Clock 기준 시간으로 설정된다")
    void createReview_setsCreatedAtFromClock() {
        CreateReviewCommand command = new CreateReviewCommand(USER_ID, PRODUCT_ID, "테스트상품", 5, "좋은 상품", "매우 만족합니다");

        given(reviewRepository.existsByUserIdAndProductId(USER_ID, PRODUCT_ID)).willReturn(false);
        given(purchaseVerificationPort.hasUserPurchasedProduct(USER_ID, PRODUCT_ID)).willReturn(true);
        given(reviewRepository.save(any(Review.class))).willAnswer(inv -> inv.getArgument(0));

        reviewCommandService.createReview(command);

        verify(reviewRepository).save(any(Review.class));
    }

    @Test
    @DisplayName("이미 리뷰를 작성한 상품에 대해 중복 리뷰 시 예외가 발생한다")
    void createReview_alreadyExists_throws() {
        CreateReviewCommand command = new CreateReviewCommand(USER_ID, PRODUCT_ID, "테스트상품", 5, "좋은 상품", "매우 만족합니다");

        given(reviewRepository.existsByUserIdAndProductId(USER_ID, PRODUCT_ID)).willReturn(true);

        assertThatThrownBy(() -> reviewCommandService.createReview(command))
                .isInstanceOf(ReviewAlreadyExistsException.class);
    }

    @Test
    @DisplayName("미구매 상품에 대해 리뷰 작성 시 예외가 발생한다")
    void createReview_notPurchased_throws() {
        CreateReviewCommand command = new CreateReviewCommand(USER_ID, PRODUCT_ID, "테스트상품", 5, "좋은 상품", "매우 만족합니다");

        given(reviewRepository.existsByUserIdAndProductId(USER_ID, PRODUCT_ID)).willReturn(false);
        given(purchaseVerificationPort.hasUserPurchasedProduct(USER_ID, PRODUCT_ID)).willReturn(false);

        assertThatThrownBy(() -> reviewCommandService.createReview(command))
                .isInstanceOf(ProductNotPurchasedException.class);
    }

    @Test
    @DisplayName("자신의 리뷰를 수정할 수 있다")
    void updateReview_ownReview_success() {
        UUID reviewId = UUID.randomUUID();
        Review review = Review.reconstitute(
                reviewId, USER_ID, PRODUCT_ID, "테스트상품", 5, "좋은 상품", "매우 만족합니다",
                ReviewStatus.ACTIVE, FIXED_TIME, FIXED_TIME);

        UpdateReviewCommand command = new UpdateReviewCommand(USER_ID, reviewId, 3, "수정된 제목", "수정된 내용");

        given(reviewRepository.findActiveById(reviewId)).willReturn(Optional.of(review));
        given(reviewRepository.save(any(Review.class))).willAnswer(inv -> inv.getArgument(0));

        UpdateReviewResult result = reviewCommandService.updateReview(command);

        assertThat(result.reviewId()).isEqualTo(reviewId);
        assertThat(review.getUpdatedAt()).isEqualTo(FIXED_TIME);
        verify(reviewEventPublisher).publish(any());
    }

    @Test
    @DisplayName("다른 사용자의 리뷰를 수정하면 예외가 발생한다")
    void updateReview_notOwner_throws() {
        UUID reviewId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        Review review = Review.reconstitute(
                reviewId, USER_ID, PRODUCT_ID, "테스트상품", 5, "좋은 상품", "매우 만족합니다",
                ReviewStatus.ACTIVE, FIXED_TIME, FIXED_TIME);

        UpdateReviewCommand command = new UpdateReviewCommand(otherUserId, reviewId, 3, "수정", "수정 내용");

        given(reviewRepository.findActiveById(reviewId)).willReturn(Optional.of(review));

        assertThatThrownBy(() -> reviewCommandService.updateReview(command))
                .isInstanceOf(ReviewAccessDeniedException.class);
    }

    @Test
    @DisplayName("존재하지 않는 리뷰를 수정하면 예외가 발생한다")
    void updateReview_notFound_throws() {
        UUID reviewId = UUID.randomUUID();
        UpdateReviewCommand command = new UpdateReviewCommand(USER_ID, reviewId, 3, "수정", "수정 내용");

        given(reviewRepository.findActiveById(reviewId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> reviewCommandService.updateReview(command))
                .isInstanceOf(ReviewNotFoundException.class);
    }

    @Test
    @DisplayName("자신의 리뷰를 삭제할 수 있다")
    void deleteReview_ownReview_success() {
        UUID reviewId = UUID.randomUUID();
        Review review = Review.reconstitute(
                reviewId, USER_ID, PRODUCT_ID, "테스트상품", 5, "좋은 상품", "매우 만족합니다",
                ReviewStatus.ACTIVE, FIXED_TIME, FIXED_TIME);

        given(reviewRepository.findActiveById(reviewId)).willReturn(Optional.of(review));
        given(reviewRepository.save(any(Review.class))).willAnswer(inv -> inv.getArgument(0));

        reviewCommandService.deleteReview(USER_ID, reviewId);

        assertThat(review.getStatus()).isEqualTo(ReviewStatus.DELETED);
        assertThat(review.getUpdatedAt()).isEqualTo(FIXED_TIME);
        verify(reviewRepository).save(any(Review.class));
        verify(reviewEventPublisher).publish(any());
    }

    @Test
    @DisplayName("이벤트 발행 실패 시 예외가 전파된다")
    void createReview_eventPublishFails_throwsException() {
        CreateReviewCommand command = new CreateReviewCommand(USER_ID, PRODUCT_ID, "테스트상품", 5, "좋은 상품", "매우 만족합니다");

        given(reviewRepository.existsByUserIdAndProductId(USER_ID, PRODUCT_ID)).willReturn(false);
        given(purchaseVerificationPort.hasUserPurchasedProduct(USER_ID, PRODUCT_ID)).willReturn(true);
        given(reviewRepository.save(any(Review.class))).willAnswer(inv -> inv.getArgument(0));
        willThrow(new RuntimeException("Kafka unavailable"))
                .given(reviewEventPublisher).publish(any());

        assertThatThrownBy(() -> reviewCommandService.createReview(command))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Kafka unavailable");
    }

    @Test
    @DisplayName("다른 사용자의 리뷰를 삭제하면 예외가 발생한다")
    void deleteReview_notOwner_throws() {
        UUID reviewId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        Review review = Review.reconstitute(
                reviewId, USER_ID, PRODUCT_ID, "테스트상품", 5, "좋은 상품", "매우 만족합니다",
                ReviewStatus.ACTIVE, FIXED_TIME, FIXED_TIME);

        given(reviewRepository.findActiveById(reviewId)).willReturn(Optional.of(review));

        assertThatThrownBy(() -> reviewCommandService.deleteReview(otherUserId, reviewId))
                .isInstanceOf(ReviewAccessDeniedException.class);
    }
}
