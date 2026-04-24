package com.example.review.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Review 애그리게이트 테스트")
class ReviewTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final Instant FIXED_TIME = Instant.parse("2026-01-01T00:00:00Z");
    private final Clock clock = Clock.fixed(FIXED_TIME, ZoneOffset.UTC);

    @Test
    @DisplayName("유효한 값으로 리뷰를 생성할 수 있다")
    void create_validInput_success() {
        Review review = Review.create(USER_ID, PRODUCT_ID, "테스트상품", 5, "좋은 상품", "매우 만족합니다", clock);

        assertThat(review.getId()).isNotNull();
        assertThat(review.getUserId()).isEqualTo(USER_ID);
        assertThat(review.getProductId()).isEqualTo(PRODUCT_ID);
        assertThat(review.getRatingValue()).isEqualTo(5);
        assertThat(review.getTitle()).isEqualTo("좋은 상품");
        assertThat(review.getContent()).isEqualTo("매우 만족합니다");
        assertThat(review.getStatus()).isEqualTo(ReviewStatus.ACTIVE);
        assertThat(review.getCreatedAt()).isEqualTo(FIXED_TIME);
        assertThat(review.getUpdatedAt()).isEqualTo(FIXED_TIME);
    }

    @Test
    @DisplayName("생성 시 createdAt과 updatedAt이 Clock 기준 시간으로 설정된다")
    void create_setsTimestampsFromClock() {
        Review review = Review.create(USER_ID, PRODUCT_ID, "테스트상품", 5, "좋은 상품", "매우 만족합니다", clock);

        assertThat(review.getCreatedAt()).isEqualTo(FIXED_TIME);
        assertThat(review.getUpdatedAt()).isEqualTo(FIXED_TIME);
    }

    @Test
    @DisplayName("Clock이 null이면 생성 시 예외가 발생한다")
    void create_nullClock_throws() {
        assertThatThrownBy(() -> Review.create(USER_ID, PRODUCT_ID, "테스트상품", 5, "제목", "내용", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("clock must not be null");
    }

    @Test
    @DisplayName("제목이 비어있으면 예외가 발생한다")
    void create_blankTitle_throws() {
        assertThatThrownBy(() -> Review.create(USER_ID, PRODUCT_ID, "테스트상품", 5, "", "내용", clock))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Review title must not be blank");
    }

    @Test
    @DisplayName("내용이 비어있으면 예외가 발생한다")
    void create_blankContent_throws() {
        assertThatThrownBy(() -> Review.create(USER_ID, PRODUCT_ID, "테스트상품", 5, "제목", "", clock))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Review content must not be blank");
    }

    @Test
    @DisplayName("제목이 null이면 예외가 발생한다")
    void create_nullTitle_throws() {
        assertThatThrownBy(() -> Review.create(USER_ID, PRODUCT_ID, "테스트상품", 5, null, "내용", clock))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Review title must not be blank");
    }

    @Test
    @DisplayName("평점이 범위를 벗어나면 예외가 발생한다")
    void create_invalidRating_throws() {
        assertThatThrownBy(() -> Review.create(USER_ID, PRODUCT_ID, "테스트상품", 0, "제목", "내용", clock))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> Review.create(USER_ID, PRODUCT_ID, "테스트상품", 6, "제목", "내용", clock))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("리뷰를 수정하면 updatedAt이 Clock 기준 시간으로 변경된다")
    void update_validInput_success() {
        Instant createTime = Instant.parse("2026-01-01T00:00:00Z");
        Instant updateTime = Instant.parse("2026-01-02T00:00:00Z");
        Clock createClock = Clock.fixed(createTime, ZoneOffset.UTC);
        Clock updateClock = Clock.fixed(updateTime, ZoneOffset.UTC);

        Review review = Review.create(USER_ID, PRODUCT_ID, "테스트상품", 5, "좋은 상품", "매우 만족합니다", createClock);

        review.update(3, "수정된 제목", "수정된 내용", updateClock);

        assertThat(review.getRatingValue()).isEqualTo(3);
        assertThat(review.getTitle()).isEqualTo("수정된 제목");
        assertThat(review.getContent()).isEqualTo("수정된 내용");
        assertThat(review.getUpdatedAt()).isEqualTo(updateTime);
        assertThat(review.getCreatedAt()).isEqualTo(createTime);
    }

    @Test
    @DisplayName("update 시 Clock이 null이면 예외가 발생한다")
    void update_nullClock_throws() {
        Review review = Review.create(USER_ID, PRODUCT_ID, "테스트상품", 5, "좋은 상품", "매우 만족합니다", clock);

        assertThatThrownBy(() -> review.update(3, "수정된 제목", "수정된 내용", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("clock must not be null");
    }

    @Test
    @DisplayName("리뷰를 소프트 삭제하면 updatedAt이 Clock 기준 시간으로 설정된다")
    void softDelete_changesStatusToDeleted() {
        Instant deleteTime = Instant.parse("2026-01-03T00:00:00Z");
        Clock deleteClock = Clock.fixed(deleteTime, ZoneOffset.UTC);

        Review review = Review.create(USER_ID, PRODUCT_ID, "테스트상품", 5, "좋은 상품", "매우 만족합니다", clock);

        review.softDelete(deleteClock);

        assertThat(review.getStatus()).isEqualTo(ReviewStatus.DELETED);
        assertThat(review.isActive()).isFalse();
        assertThat(review.getUpdatedAt()).isEqualTo(deleteTime);
    }

    @Test
    @DisplayName("softDelete 시 Clock이 null이면 예외가 발생한다")
    void softDelete_nullClock_throws() {
        Review review = Review.create(USER_ID, PRODUCT_ID, "테스트상품", 5, "좋은 상품", "매우 만족합니다", clock);

        assertThatThrownBy(() -> review.softDelete(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("clock must not be null");
    }

    @Test
    @DisplayName("리뷰 소유자 확인이 정상 동작한다")
    void isOwnedBy_correctUser_returnsTrue() {
        Review review = Review.create(USER_ID, PRODUCT_ID, "테스트상품", 5, "좋은 상품", "매우 만족합니다", clock);

        assertThat(review.isOwnedBy(USER_ID)).isTrue();
        assertThat(review.isOwnedBy(UUID.randomUUID())).isFalse();
    }

    @Test
    @DisplayName("제목 앞뒤 공백이 제거된다")
    void create_titleWithSpaces_trimmed() {
        Review review = Review.create(USER_ID, PRODUCT_ID, "테스트상품", 5, "  좋은 상품  ", "매우 만족합니다", clock);

        assertThat(review.getTitle()).isEqualTo("좋은 상품");
    }
}
