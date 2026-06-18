package com.example.search.adapter.outbound.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.example.search.application.dto.SearchProductQuery;
import com.example.search.domain.model.SearchFilter;
import com.example.search.domain.model.SearchSort;
import com.example.search.domain.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * AC-3 (TASK-BE-404): every SearchService read query includes a mandatory
 * tenant_id filter clause from TenantContext. Verified at query-construction
 * level — no live ES needed.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("Elasticsearch 쿼리 tenant_id 필터 포함 검증 (TASK-BE-404 AC-3)")
@SuppressWarnings({"unchecked", "rawtypes"})
class ElasticsearchQueryTenantFilterTest {

    @InjectMocks
    private ElasticsearchQueryAdapter adapter;

    @Mock
    private ElasticsearchClient elasticsearchClient;

    @Mock
    private IndexProperties indexProperties;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("TenantContext='store-a'일 때 bool.filter에 tenantId=store-a가 포함된다")
    void search_tenantContextSet_queryContainsTenantFilter() throws Exception {
        TenantContext.set("store-a");
        given(indexProperties.name()).willReturn("products");
        given(elasticsearchClient.search(any(SearchRequest.class), any(Class.class)))
                .willReturn(emptySearchResponse());

        SearchProductQuery query = new SearchProductQuery(
                SearchFilter.of("노트북", null, null, null, null),
                SearchSort.RELEVANCE, 0, 20
        );

        adapter.search(query);

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(elasticsearchClient).search(captor.capture(), any(Class.class));

        SearchRequest request = captor.getValue();
        Query builtQuery = request.query();
        assertThat(builtQuery).isNotNull();
        assertThat(builtQuery.isBool()).isTrue();

        BoolQuery boolQuery = builtQuery.bool();
        List<Query> filters = boolQuery.filter();
        assertThat(filters).isNotEmpty();

        boolean hasTenantFilter = filters.stream()
                .filter(Query::isTerm)
                .map(Query::term)
                .anyMatch(term -> "tenantId".equals(term.field())
                        && "store-a".equals(term.value().stringValue()));
        assertThat(hasTenantFilter)
                .as("bool.filter must include a term query on tenantId=store-a")
                .isTrue();
    }

    @Test
    @DisplayName("TenantContext 미설정 시 기본값 'ecommerce'로 tenant 필터가 적용된다 (D8 net-zero)")
    void search_noTenantContext_queryScopedToDefaultTenant() throws Exception {
        // no TenantContext set — should default to 'ecommerce'
        given(indexProperties.name()).willReturn("products");
        given(elasticsearchClient.search(any(SearchRequest.class), any(Class.class)))
                .willReturn(emptySearchResponse());

        SearchProductQuery query = new SearchProductQuery(
                SearchFilter.of("키보드", null, null, null, null),
                SearchSort.RELEVANCE, 0, 20
        );

        adapter.search(query);

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(elasticsearchClient).search(captor.capture(), any(Class.class));

        BoolQuery boolQuery = captor.getValue().query().bool();
        boolean hasDefaultTenantFilter = boolQuery.filter().stream()
                .filter(Query::isTerm)
                .map(Query::term)
                .anyMatch(term -> "tenantId".equals(term.field())
                        && "ecommerce".equals(term.value().stringValue()));
        assertThat(hasDefaultTenantFilter)
                .as("bool.filter must include tenantId=ecommerce when no tenant is set")
                .isTrue();
    }

    @Test
    @DisplayName("tenant-a와 tenant-b 컨텍스트에서 각각 다른 tenant 필터가 생성된다 (cross-tenant isolation)")
    void search_differentTenants_differentFilters() throws Exception {
        given(indexProperties.name()).willReturn("products");
        given(elasticsearchClient.search(any(SearchRequest.class), any(Class.class)))
                .willReturn(emptySearchResponse(), emptySearchResponse());

        // First search under tenant-a
        TenantContext.set("tenant-a");
        adapter.search(new SearchProductQuery(
                SearchFilter.of("상품", null, null, null, null), SearchSort.RELEVANCE, 0, 10));
        TenantContext.clear();

        // Second search under tenant-b
        TenantContext.set("tenant-b");
        adapter.search(new SearchProductQuery(
                SearchFilter.of("상품", null, null, null, null), SearchSort.RELEVANCE, 0, 10));

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(elasticsearchClient, org.mockito.Mockito.times(2))
                .search(captor.capture(), any(Class.class));

        List<SearchRequest> requests = captor.getAllValues();
        String tenantInFirst = extractTenantFilterValue(requests.get(0));
        String tenantInSecond = extractTenantFilterValue(requests.get(1));

        assertThat(tenantInFirst).isEqualTo("tenant-a");
        assertThat(tenantInSecond).isEqualTo("tenant-b");
        assertThat(tenantInFirst).isNotEqualTo(tenantInSecond);
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private String extractTenantFilterValue(SearchRequest request) {
        return request.query().bool().filter().stream()
                .filter(Query::isTerm)
                .map(Query::term)
                .filter(term -> "tenantId".equals(term.field()))
                .map(TermQuery::value)
                .map(fv -> fv.stringValue())
                .findFirst()
                .orElse(null);
    }

    private SearchResponse<Map> emptySearchResponse() {
        return org.mockito.Mockito.mock(SearchResponse.class,
                org.mockito.Mockito.withSettings().defaultAnswer(invocation -> {
                    Class<?> returnType = invocation.getMethod().getReturnType();
                    if (returnType == boolean.class || returnType == Boolean.class) return false;
                    if (returnType == long.class || returnType == Long.class) return 0L;
                    if (returnType == int.class || returnType == Integer.class) return 0;
                    return null;
                }));
    }
}
