package com.example.search.adapter.outbound.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.ElasticsearchIndicesClient;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;

import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("IndexInitializer 단위 테스트")
class IndexInitializerUnitTest {

    @InjectMocks
    private IndexInitializer indexInitializer;

    @Mock
    private ElasticsearchClient elasticsearchClient;

    @Mock
    private IndexProperties indexProperties;

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("인덱스가 존재하지 않으면 새 인덱스를 생성한다")
    void run_indexNotExists_createsIndex() throws Exception {
        given(indexProperties.name()).willReturn("products");
        ElasticsearchIndicesClient indicesClient = mock(ElasticsearchIndicesClient.class);
        given(elasticsearchClient.indices()).willReturn(indicesClient);
        given(indicesClient.exists(any(ExistsRequest.class))).willReturn(new BooleanResponse(false));
        given(indicesClient.create(any(Function.class))).willReturn(null);

        indexInitializer.run(mock(ApplicationArguments.class));

        verify(indicesClient).create(any(Function.class));
    }

    @Test
    @DisplayName("인덱스가 이미 존재하면 생성을 건너뛴다")
    void run_indexExists_skipsCreation() throws Exception {
        given(indexProperties.name()).willReturn("products");
        ElasticsearchIndicesClient indicesClient = mock(ElasticsearchIndicesClient.class);
        given(elasticsearchClient.indices()).willReturn(indicesClient);
        given(indicesClient.exists(any(ExistsRequest.class))).willReturn(new BooleanResponse(true));

        indexInitializer.run(mock(ApplicationArguments.class));

        verify(indicesClient, never()).create(any(co.elastic.clients.elasticsearch.indices.CreateIndexRequest.class));
    }

    @Test
    @DisplayName("Elasticsearch 연결 실패 시 예외가 전파된다")
    void run_connectionFailure_throwsException() throws Exception {
        given(indexProperties.name()).willReturn("products");
        ElasticsearchIndicesClient indicesClient = mock(ElasticsearchIndicesClient.class);
        given(elasticsearchClient.indices()).willReturn(indicesClient);
        given(indicesClient.exists(any(ExistsRequest.class))).willThrow(new RuntimeException("Connection refused"));

        assertThatThrownBy(() -> indexInitializer.run(mock(ApplicationArguments.class)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Connection refused");
    }
}
