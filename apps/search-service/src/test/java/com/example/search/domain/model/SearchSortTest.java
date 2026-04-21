package com.example.search.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SearchSort 단위 테스트")
class SearchSortTest {

    @Test
    @DisplayName("null 입력 시 RELEVANCE가 반환된다")
    void from_null_returnsRelevance() {
        assertThat(SearchSort.from(null)).isEqualTo(SearchSort.RELEVANCE);
    }

    @Test
    @DisplayName("price_asc 입력 시 PRICE_ASC가 반환된다")
    void from_priceAsc_returnsPriceAsc() {
        assertThat(SearchSort.from("price_asc")).isEqualTo(SearchSort.PRICE_ASC);
    }

    @Test
    @DisplayName("price_desc 입력 시 PRICE_DESC가 반환된다")
    void from_priceDesc_returnsPriceDesc() {
        assertThat(SearchSort.from("price_desc")).isEqualTo(SearchSort.PRICE_DESC);
    }

    @Test
    @DisplayName("newest 입력 시 NEWEST가 반환된다")
    void from_newest_returnsNewest() {
        assertThat(SearchSort.from("newest")).isEqualTo(SearchSort.NEWEST);
    }

    @Test
    @DisplayName("대소문자 무관하게 파싱된다")
    void from_upperCase_parsedCorrectly() {
        assertThat(SearchSort.from("PRICE_ASC")).isEqualTo(SearchSort.PRICE_ASC);
        assertThat(SearchSort.from("Price_Desc")).isEqualTo(SearchSort.PRICE_DESC);
        assertThat(SearchSort.from("NEWEST")).isEqualTo(SearchSort.NEWEST);
    }

    @Test
    @DisplayName("알 수 없는 값 입력 시 RELEVANCE가 반환된다")
    void from_unknownValue_returnsRelevance() {
        assertThat(SearchSort.from("unknown")).isEqualTo(SearchSort.RELEVANCE);
        assertThat(SearchSort.from("")).isEqualTo(SearchSort.RELEVANCE);
        assertThat(SearchSort.from("relevance")).isEqualTo(SearchSort.RELEVANCE);
    }
}
