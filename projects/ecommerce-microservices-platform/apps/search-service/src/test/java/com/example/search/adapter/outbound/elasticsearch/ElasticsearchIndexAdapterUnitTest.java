package com.example.search.adapter.outbound.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.UpdateRequest;
import com.example.search.domain.model.SearchDocument;
import com.example.search.application.exception.SearchException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
@DisplayName("ElasticsearchIndexAdapter 단위 테스트")
class ElasticsearchIndexAdapterUnitTest {

    @InjectMocks
    private ElasticsearchIndexAdapter adapter;

    @Mock
    private ElasticsearchClient elasticsearchClient;

    @Mock
    private IndexProperties indexProperties;

    @Test
    @DisplayName("upsert 실패 시 SearchException이 발생한다")
    @SuppressWarnings("unchecked")
    void upsert_failure_throwsSearchException() throws Exception {
        given_indexName();
        doThrow(new IOException("Connection refused"))
                .when(elasticsearchClient).index(any(IndexRequest.class));

        SearchDocument document = SearchDocument.of("p1", "노트북", "설명", 1000L, "ON_SALE", "cat1", 10);

        assertThatThrownBy(() -> adapter.upsert(document))
                .isInstanceOf(SearchException.class)
                .hasMessageContaining("Failed to upsert search index");
    }

    @Test
    @DisplayName("updateStock 실패 시 SearchException이 발생한다")
    @SuppressWarnings("unchecked")
    void updateStock_failure_throwsSearchException() throws Exception {
        given_indexName();
        doThrow(new IOException("Connection refused"))
                .when(elasticsearchClient).update(any(UpdateRequest.class), any(Class.class));

        assertThatThrownBy(() -> adapter.updateStock("p1", 0, "SOLD_OUT"))
                .isInstanceOf(SearchException.class)
                .hasMessageContaining("Failed to update stock");
    }

    private void given_indexName() {
        org.mockito.BDDMockito.given(indexProperties.name()).willReturn("products");
    }
}
