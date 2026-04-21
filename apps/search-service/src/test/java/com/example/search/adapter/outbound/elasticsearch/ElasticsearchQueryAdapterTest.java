package com.example.search.adapter.outbound.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import co.elastic.clients.elasticsearch.core.search.TotalHits;
import co.elastic.clients.elasticsearch.core.search.TotalHitsRelation;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.example.search.application.dto.SearchProductQuery;
import com.example.search.application.dto.SearchProductResult;
import com.example.search.domain.model.SearchFilter;
import com.example.search.domain.model.SearchSort;
import com.example.search.application.exception.SearchException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@DisplayName("ElasticsearchQueryAdapter 단위 테스트")
class ElasticsearchQueryAdapterTest {

    @InjectMocks
    private ElasticsearchQueryAdapter adapter;

    @Mock
    private ElasticsearchClient elasticsearchClient;

    @Mock
    private IndexProperties indexProperties;

    private SearchProductQuery createQuery() {
        SearchFilter filter = SearchFilter.of("노트북", null, null, null, null);
        return new SearchProductQuery(filter, SearchSort.RELEVANCE, 0, 20);
    }

    @Test
    @DisplayName("Elasticsearch 연결 실패 시 SearchException이 발생한다")
    @SuppressWarnings("unchecked")
    void search_connectionFailure_throwsSearchException() throws Exception {
        given(indexProperties.name()).willReturn("products");
        given(elasticsearchClient.search(any(co.elastic.clients.elasticsearch.core.SearchRequest.class), eq(Map.class)))
                .willThrow(new IOException("Connection refused"));

        assertThatThrownBy(() -> adapter.search(createQuery()))
                .isInstanceOf(SearchException.class)
                .hasMessageContaining("Search failed")
                .hasCauseInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("response.hits()가 null이면 빈 결과를 반환한다")
    @SuppressWarnings("unchecked")
    void search_nullHits_returnsEmptyResult() throws Exception {
        given(indexProperties.name()).willReturn("products");

        SearchResponse<Map> response = mock(SearchResponse.class);
        given(response.hits()).willReturn(null);
        given(elasticsearchClient.search(any(co.elastic.clients.elasticsearch.core.SearchRequest.class), eq(Map.class)))
                .willReturn(response);

        SearchProductResult result = adapter.search(createQuery());

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isEqualTo(0L);
        assertThat(result.facets().categories()).isEmpty();
        assertThat(result.facets().priceRanges()).isEmpty();
    }

    @Test
    @DisplayName("빈 hits 목록이면 빈 문서 리스트를 반환한다")
    @SuppressWarnings("unchecked")
    void search_emptyHits_returnsEmptyDocuments() throws Exception {
        given(indexProperties.name()).willReturn("products");

        HitsMetadata<Map> hitsMetadata = mock(HitsMetadata.class);
        given(hitsMetadata.hits()).willReturn(Collections.emptyList());
        given(hitsMetadata.total()).willReturn(TotalHits.of(t -> t.value(0L).relation(TotalHitsRelation.Eq)));

        SearchResponse<Map> response = mock(SearchResponse.class);
        given(response.hits()).willReturn(hitsMetadata);
        given(response.aggregations()).willReturn(Collections.emptyMap());
        given(elasticsearchClient.search(any(co.elastic.clients.elasticsearch.core.SearchRequest.class), eq(Map.class)))
                .willReturn(response);

        SearchProductResult result = adapter.search(createQuery());

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isEqualTo(0L);
    }

    @Test
    @DisplayName("hit의 source가 null이면 해당 문서를 건너뛴다")
    @SuppressWarnings("unchecked")
    void search_nullSource_skipsDocument() throws Exception {
        given(indexProperties.name()).willReturn("products");

        Hit<Map> hitWithNullSource = mock(Hit.class);
        given(hitWithNullSource.source()).willReturn(null);

        Hit<Map> hitWithSource = mock(Hit.class);
        Map<String, Object> source = Map.of(
                "productId", "p1",
                "name", "노트북",
                "description", "설명",
                "price", 1000000,
                "status", "ON_SALE",
                "categoryId", "cat1",
                "totalStock", 10
        );
        given(hitWithSource.source()).willReturn(source);
        given(hitWithSource.score()).willReturn(1.5);

        HitsMetadata<Map> hitsMetadata = mock(HitsMetadata.class);
        given(hitsMetadata.hits()).willReturn(List.of(hitWithNullSource, hitWithSource));
        given(hitsMetadata.total()).willReturn(TotalHits.of(t -> t.value(2L).relation(TotalHitsRelation.Eq)));

        SearchResponse<Map> response = mock(SearchResponse.class);
        given(response.hits()).willReturn(hitsMetadata);
        given(response.aggregations()).willReturn(Collections.emptyMap());
        given(elasticsearchClient.search(any(co.elastic.clients.elasticsearch.core.SearchRequest.class), eq(Map.class)))
                .willReturn(response);

        SearchProductResult result = adapter.search(createQuery());

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).productId()).isEqualTo("p1");
    }

    @Test
    @DisplayName("total이 null이면 totalElements는 0을 반환한다")
    @SuppressWarnings("unchecked")
    void search_nullTotal_returnsTotalElementsZero() throws Exception {
        given(indexProperties.name()).willReturn("products");

        HitsMetadata<Map> hitsMetadata = mock(HitsMetadata.class);
        given(hitsMetadata.hits()).willReturn(Collections.emptyList());
        given(hitsMetadata.total()).willReturn(null);

        SearchResponse<Map> response = mock(SearchResponse.class);
        given(response.hits()).willReturn(hitsMetadata);
        given(response.aggregations()).willReturn(Collections.emptyMap());
        given(elasticsearchClient.search(any(co.elastic.clients.elasticsearch.core.SearchRequest.class), eq(Map.class)))
                .willReturn(response);

        SearchProductResult result = adapter.search(createQuery());

        assertThat(result.totalElements()).isEqualTo(0L);
    }
}
