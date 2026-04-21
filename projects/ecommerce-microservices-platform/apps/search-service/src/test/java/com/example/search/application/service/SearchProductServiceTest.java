package com.example.search.application.service;

import com.example.search.application.dto.SearchProductQuery;
import com.example.search.application.dto.SearchProductResult;
import com.example.search.domain.model.FacetResult;
import com.example.search.domain.model.SearchDocument;
import com.example.search.domain.model.SearchFilter;
import com.example.search.domain.model.SearchSort;
import com.example.search.application.port.out.SearchMetricsPort;
import com.example.search.application.port.out.SearchQueryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("SearchProductService 단위 테스트")
class SearchProductServiceTest {

    @InjectMocks
    private SearchProductService searchProductService;

    @Mock
    private SearchQueryPort searchQueryPort;

    @Mock
    private SearchMetricsPort searchMetrics;

    @BeforeEach
    void setUp() {
        given(searchMetrics.recordSearchQueryDuration(any(Supplier.class))).willAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(0);
            return supplier.get();
        });
    }

    @Test
    @DisplayName("검색 성공 - SearchQueryPort가 호출되고 결과를 반환한다")
    void search_validQuery_callsPortAndReturnsResult() {
        SearchFilter filter = SearchFilter.of("노트북", null, null, null, null);
        SearchProductQuery query = new SearchProductQuery(filter, SearchSort.RELEVANCE, 0, 20);
        SearchProductResult expected = new SearchProductResult(
                List.of(SearchDocument.of("p1", "노트북", "설명", 1000L, "ON_SALE", "cat1", 10)),
                new FacetResult(List.of(), List.of()),
                1L
        );
        given(searchQueryPort.search(query)).willReturn(expected);

        SearchProductResult result = searchProductService.search(query);

        assertThat(result).isEqualTo(expected);
        verify(searchQueryPort).search(query);
    }

    @Test
    @DisplayName("검색 결과 없음 - 빈 리스트와 totalElements=0 반환")
    void search_noResults_returnsEmptyResult() {
        SearchFilter filter = SearchFilter.of("존재하지않는상품xyz", null, null, null, null);
        SearchProductQuery query = new SearchProductQuery(filter, SearchSort.RELEVANCE, 0, 20);
        SearchProductResult empty = new SearchProductResult(
                List.of(),
                new FacetResult(List.of(), List.of()),
                0L
        );
        given(searchQueryPort.search(query)).willReturn(empty);

        SearchProductResult result = searchProductService.search(query);

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isEqualTo(0L);
    }

    @Test
    @DisplayName("필터 조합 - SearchQueryPort에 올바른 쿼리가 전달된다")
    void search_withFilters_passesQueryToPort() {
        SearchFilter filter = SearchFilter.of("노트북", "cat1", 100000L, 2000000L, "ON_SALE");
        SearchProductQuery query = new SearchProductQuery(filter, SearchSort.PRICE_ASC, 0, 10);
        given(searchQueryPort.search(any())).willReturn(
                new SearchProductResult(List.of(), new FacetResult(List.of(), List.of()), 0L)
        );

        searchProductService.search(query);

        ArgumentCaptor<SearchProductQuery> captor = ArgumentCaptor.forClass(SearchProductQuery.class);
        verify(searchQueryPort).search(captor.capture());
        assertThat(captor.getValue().filter().categoryId()).isEqualTo("cat1");
        assertThat(captor.getValue().sort()).isEqualTo(SearchSort.PRICE_ASC);
    }

    @Test
    @DisplayName("검색 성공 시 search_query_total 메트릭이 증가한다")
    void search_success_incrementsSearchQueryMetric() {
        SearchFilter filter = SearchFilter.of("노트북", null, null, null, null);
        SearchProductQuery query = new SearchProductQuery(filter, SearchSort.RELEVANCE, 0, 20);
        given(searchQueryPort.search(query)).willReturn(
                new SearchProductResult(
                        List.of(SearchDocument.of("p1", "노트북", "설명", 1000L, "ON_SALE", "cat1", 10)),
                        new FacetResult(List.of(), List.of()), 1L
                )
        );

        searchProductService.search(query);

        verify(searchMetrics).incrementSearchQuery();
    }

    @Test
    @DisplayName("검색 결과 0건이면 zero_results 메트릭이 증가한다")
    void search_emptyResult_incrementsZeroResultsMetric() {
        SearchFilter filter = SearchFilter.of("없는상품", null, null, null, null);
        SearchProductQuery query = new SearchProductQuery(filter, SearchSort.RELEVANCE, 0, 20);
        given(searchQueryPort.search(query)).willReturn(
                new SearchProductResult(List.of(), new FacetResult(List.of(), List.of()), 0L)
        );

        searchProductService.search(query);

        verify(searchMetrics).incrementZeroResults();
    }

    @Test
    @DisplayName("검색 결과가 있으면 zero_results 메트릭이 증가하지 않는다")
    void search_nonEmptyResult_doesNotIncrementZeroResults() {
        SearchFilter filter = SearchFilter.of("노트북", null, null, null, null);
        SearchProductQuery query = new SearchProductQuery(filter, SearchSort.RELEVANCE, 0, 20);
        given(searchQueryPort.search(query)).willReturn(
                new SearchProductResult(
                        List.of(SearchDocument.of("p1", "노트북", "설명", 1000L, "ON_SALE", "cat1", 10)),
                        new FacetResult(List.of(), List.of()), 1L
                )
        );

        searchProductService.search(query);

        verify(searchMetrics, org.mockito.Mockito.never()).incrementZeroResults();
    }
}
