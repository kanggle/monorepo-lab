package com.example.search.adapter.outbound.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.TextProperty;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.DeleteIndexRequest;
import co.elastic.clients.elasticsearch.indices.ElasticsearchIndicesClient;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.elasticsearch.indices.GetMappingResponse;
import co.elastic.clients.elasticsearch.indices.get_mapping.IndexMappingRecord;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;

import java.util.Map;
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

    @Test
    @DisplayName("인덱스가 존재하지 않으면 새 인덱스를 생성한다")
    void run_indexNotExists_createsIndex() throws Exception {
        given(indexProperties.name()).willReturn("products");
        ElasticsearchIndicesClient indicesClient = mock(ElasticsearchIndicesClient.class);
        given(elasticsearchClient.indices()).willReturn(indicesClient);
        given(indicesClient.exists(any(ExistsRequest.class))).willReturn(new BooleanResponse(false));
        given(indicesClient.create(any(CreateIndexRequest.class))).willReturn(null);

        indexInitializer.run(mock(ApplicationArguments.class));

        verify(indicesClient).create(any(CreateIndexRequest.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("인덱스가 존재하고 현재 spec 과 일치하면 생성을 건너뛴다")
    void run_indexExists_currentSpec_skipsCreation() throws Exception {
        given(indexProperties.name()).willReturn("products");
        ElasticsearchIndicesClient indicesClient = mock(ElasticsearchIndicesClient.class);
        given(elasticsearchClient.indices()).willReturn(indicesClient);
        given(indicesClient.exists(any(ExistsRequest.class))).willReturn(new BooleanResponse(true));

        // hasCurrentSpec(indexName) 의 deep call chain 을 'name=text/nori_korean' + 'thumbnailUrl 존재'
        // + 'tenantId 존재' 매핑으로 stub — production code 의 세 분기를 모두 통과시켜야 한다 (TASK-BE-404).
        GetMappingResponse mappingResp = mock(GetMappingResponse.class);
        IndexMappingRecord mappingRecord = mock(IndexMappingRecord.class);
        TypeMapping typeMapping = mock(TypeMapping.class);
        Property nameProp = mock(Property.class);
        Property thumbnailProp = mock(Property.class);
        Property tenantIdProp = mock(Property.class);
        TextProperty nameText = mock(TextProperty.class);

        given(indicesClient.getMapping(any(Function.class))).willReturn(mappingResp);
        given(mappingResp.result()).willReturn(Map.of("products", mappingRecord));
        given(mappingRecord.mappings()).willReturn(typeMapping);
        given(typeMapping.properties()).willReturn(Map.of("name", nameProp, "thumbnailUrl", thumbnailProp, "tenantId", tenantIdProp));
        given(nameProp.isText()).willReturn(true);
        given(nameProp.text()).willReturn(nameText);
        given(nameText.analyzer()).willReturn("nori_korean");

        indexInitializer.run(mock(ApplicationArguments.class));

        verify(indicesClient, never()).create(any(CreateIndexRequest.class));
        verify(indicesClient, never()).delete(any(DeleteIndexRequest.class));
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
