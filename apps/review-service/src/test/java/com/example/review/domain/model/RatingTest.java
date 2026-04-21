package com.example.review.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Rating 값 객체 테스트")
class RatingTest {

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5})
    @DisplayName("유효한 평점 값(1-5)으로 생성할 수 있다")
    void create_validValues_success(int value) {
        Rating rating = new Rating(value);
        assertThat(rating.value()).isEqualTo(value);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 6, 100})
    @DisplayName("범위를 벗어난 평점 값은 예외가 발생한다")
    void create_invalidValues_throws(int value) {
        assertThatThrownBy(() -> new Rating(value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Rating must be between");
    }

    @Test
    @DisplayName("동일한 값의 Rating은 equals가 true다")
    void equals_sameValue_true() {
        assertThat(new Rating(5)).isEqualTo(new Rating(5));
    }

    @Test
    @DisplayName("다른 값의 Rating은 equals가 false다")
    void equals_differentValue_false() {
        assertThat(new Rating(5)).isNotEqualTo(new Rating(4));
    }
}
