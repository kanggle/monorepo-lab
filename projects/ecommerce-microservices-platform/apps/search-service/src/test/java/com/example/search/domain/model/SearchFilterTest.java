package com.example.search.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SearchFilter 단위 테스트")
class SearchFilterTest {

    @Test
    @DisplayName("정상 생성 - keyword만 필수, 나머지는 선택")
    void of_keywordOnly_createsFilter() {
        SearchFilter filter = SearchFilter.of("노트북", null, null, null, null);

        assertThat(filter.keyword()).isEqualTo("노트북");
        assertThat(filter.categoryId()).isNull();
        assertThat(filter.minPrice()).isNull();
        assertThat(filter.maxPrice()).isNull();
        assertThat(filter.status()).isEqualTo("ON_SALE");
    }

    @Test
    @DisplayName("keyword null이면 예외가 발생한다")
    void of_nullKeyword_throwsException() {
        assertThatThrownBy(() -> SearchFilter.of(null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("keyword");
    }

    @Test
    @DisplayName("keyword가 blank이면 예외가 발생한다")
    void of_blankKeyword_throwsException() {
        assertThatThrownBy(() -> SearchFilter.of("   ", null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("keyword");
    }

    @Test
    @DisplayName("minPrice가 음수이면 예외가 발생한다")
    void of_negativeMinPrice_throwsException() {
        assertThatThrownBy(() -> SearchFilter.of("노트북", null, -1L, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minPrice");
    }

    @Test
    @DisplayName("maxPrice가 음수이면 예외가 발생한다")
    void of_negativeMaxPrice_throwsException() {
        assertThatThrownBy(() -> SearchFilter.of("노트북", null, null, -1L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxPrice");
    }

    @Test
    @DisplayName("minPrice가 maxPrice보다 크면 예외가 발생한다")
    void of_minPriceGreaterThanMaxPrice_throwsException() {
        assertThatThrownBy(() -> SearchFilter.of("노트북", null, 50000L, 10000L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minPrice");
    }

    @Test
    @DisplayName("모든 필드 정상 설정 - 필터가 올바르게 생성된다")
    void of_allFields_createsFilter() {
        SearchFilter filter = SearchFilter.of("노트북", "cat1", 10000L, 50000L, "ON_SALE");

        assertThat(filter.keyword()).isEqualTo("노트북");
        assertThat(filter.categoryId()).isEqualTo("cat1");
        assertThat(filter.minPrice()).isEqualTo(10000L);
        assertThat(filter.maxPrice()).isEqualTo(50000L);
        assertThat(filter.status()).isEqualTo("ON_SALE");
    }

    @Test
    @DisplayName("status 미전달 시 기본값 ON_SALE이 적용된다")
    void of_nullStatus_defaultsToOnSale() {
        SearchFilter filter = SearchFilter.of("노트북", null, null, null, null);

        assertThat(filter.status()).isEqualTo("ON_SALE");
    }

    @Test
    @DisplayName("keyword의 앞뒤 공백이 제거된다")
    void of_keywordWithWhitespace_trimmed() {
        SearchFilter filter = SearchFilter.of("  노트북  ", null, null, null, null);

        assertThat(filter.keyword()).isEqualTo("노트북");
    }

    @Test
    @DisplayName("minPrice와 maxPrice가 같으면 정상 생성된다")
    void of_minPriceEqualsMaxPrice_createsFilter() {
        SearchFilter filter = SearchFilter.of("노트북", null, 10000L, 10000L, null);

        assertThat(filter.minPrice()).isEqualTo(10000L);
        assertThat(filter.maxPrice()).isEqualTo(10000L);
    }
}
