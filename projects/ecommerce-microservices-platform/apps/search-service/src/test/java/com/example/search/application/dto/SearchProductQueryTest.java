package com.example.search.application.dto;

import com.example.search.domain.model.SearchFilter;
import com.example.search.domain.model.SearchSort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SearchProductQuery 단위 테스트")
class SearchProductQueryTest {

    private SearchFilter defaultFilter() {
        return SearchFilter.of("노트북", null, null, null, null);
    }

    @Test
    @DisplayName("정상 생성 - 모든 필드가 올바르게 설정된다")
    void constructor_validParams_createsQuery() {
        SearchProductQuery query = new SearchProductQuery(defaultFilter(), SearchSort.RELEVANCE, 0, 20);

        assertThat(query.filter().keyword()).isEqualTo("노트북");
        assertThat(query.sort()).isEqualTo(SearchSort.RELEVANCE);
        assertThat(query.page()).isEqualTo(0);
        assertThat(query.size()).isEqualTo(20);
    }

    @Test
    @DisplayName("size가 0이면 예외가 발생한다")
    void constructor_sizeZero_throwsException() {
        assertThatThrownBy(() -> new SearchProductQuery(defaultFilter(), SearchSort.RELEVANCE, 0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("size");
    }

    @Test
    @DisplayName("size가 음수이면 예외가 발생한다")
    void constructor_negativeSize_throwsException() {
        assertThatThrownBy(() -> new SearchProductQuery(defaultFilter(), SearchSort.RELEVANCE, 0, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("size");
    }

    @Test
    @DisplayName("size가 100 초과이면 100으로 제한된다")
    void constructor_sizeOver100_cappedAt100() {
        SearchProductQuery query = new SearchProductQuery(defaultFilter(), SearchSort.RELEVANCE, 0, 150);

        assertThat(query.size()).isEqualTo(100);
    }

    @Test
    @DisplayName("size가 100이면 그대로 유지된다")
    void constructor_sizeExactly100_staysAt100() {
        SearchProductQuery query = new SearchProductQuery(defaultFilter(), SearchSort.RELEVANCE, 0, 100);

        assertThat(query.size()).isEqualTo(100);
    }

    @Test
    @DisplayName("size가 1이면 정상 생성된다")
    void constructor_sizeOne_createsQuery() {
        SearchProductQuery query = new SearchProductQuery(defaultFilter(), SearchSort.RELEVANCE, 0, 1);

        assertThat(query.size()).isEqualTo(1);
    }
}
