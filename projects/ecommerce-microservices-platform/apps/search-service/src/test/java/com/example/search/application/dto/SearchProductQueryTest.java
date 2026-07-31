package com.example.search.application.dto;

import com.example.common.page.PageQuery;
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
        SearchProductQuery query = new SearchProductQuery(
                defaultFilter(), SearchSort.RELEVANCE, new PageQuery(0, 20, null, null));

        assertThat(query.filter().keyword()).isEqualTo("노트북");
        assertThat(query.sort()).isEqualTo(SearchSort.RELEVANCE);
        assertThat(query.page()).isEqualTo(0);
        assertThat(query.size()).isEqualTo(20);
    }

    // size validation is delegated to the shared PageQuery (ADR-MONO-058 D3 —
    // TASK-BE-567); exhaustive clamp/throw-matrix coverage lives in
    // libs/java-common's own PageQueryTest, not duplicated here. These two cases
    // confirm the delegation actually reaches PageQuery's validation.

    @Test
    @DisplayName("size가 0이면 PageQuery 생성 시점에 예외가 발생한다")
    void pageQuery_sizeZero_throwsException() {
        assertThatThrownBy(() -> new PageQuery(0, 0, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("size");
    }

    @Test
    @DisplayName("size가 음수이면 PageQuery 생성 시점에 예외가 발생한다")
    void pageQuery_negativeSize_throwsException() {
        assertThatThrownBy(() -> new PageQuery(0, -1, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("size");
    }

    @Test
    @DisplayName("PageQuery.of()로 생성하면 size가 100으로 clamp된다 (컨트롤러 경계에서 사용하는 경로)")
    void pageQueryOf_sizeOver100_clampsTo100() {
        SearchProductQuery query = new SearchProductQuery(
                defaultFilter(), SearchSort.RELEVANCE, PageQuery.of(0, 150, null, null));

        assertThat(query.size()).isEqualTo(100);
    }

    @Test
    @DisplayName("size가 100이면 그대로 유지된다")
    void constructor_sizeExactly100_staysAt100() {
        SearchProductQuery query = new SearchProductQuery(
                defaultFilter(), SearchSort.RELEVANCE, new PageQuery(0, 100, null, null));

        assertThat(query.size()).isEqualTo(100);
    }

    @Test
    @DisplayName("size가 1이면 정상 생성된다")
    void constructor_sizeOne_createsQuery() {
        SearchProductQuery query = new SearchProductQuery(
                defaultFilter(), SearchSort.RELEVANCE, new PageQuery(0, 1, null, null));

        assertThat(query.size()).isEqualTo(1);
    }
}
