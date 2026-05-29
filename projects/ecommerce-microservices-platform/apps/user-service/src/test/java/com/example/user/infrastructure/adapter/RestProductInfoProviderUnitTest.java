package com.example.user.infrastructure.adapter;

import com.example.user.domain.service.ProductInfoProvider.ProductInfo;
import com.example.user.infrastructure.adapter.RestProductInfoProvider.ProductDetailResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("RestProductInfoProvider 단위 테스트")
@SuppressWarnings({"rawtypes", "unchecked"})
class RestProductInfoProviderUnitTest {

    @Mock
    private RestClient restClient;

    @Mock
    private RestClient.RequestHeadersUriSpec uriSpec;

    private RestProductInfoProvider provider;

    private static final String BASE_URL = "http://localhost:8082";
    private static final String URL_TEMPLATE = BASE_URL + "/api/products/{productId}";

    @BeforeEach
    void setUp() {
        provider = new RestProductInfoProvider(restClient, BASE_URL);
    }

    private void stubProduct(UUID productId, ProductDetailResponse response) {
        RestClient.ResponseSpec responseSpec = stubChain(productId);
        given(responseSpec.body(ProductDetailResponse.class)).willReturn(response);
    }

    private void stubProductFailure(UUID productId, RuntimeException error) {
        RestClient.ResponseSpec responseSpec = stubChain(productId);
        given(responseSpec.body(ProductDetailResponse.class)).willThrow(error);
    }

    private RestClient.ResponseSpec stubChain(UUID productId) {
        RestClient.RequestHeadersSpec requestSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);
        given(uriSpec.uri(URL_TEMPLATE, productId)).willReturn(requestSpec);
        given(requestSpec.retrieve()).willReturn(responseSpec);
        return responseSpec;
    }

    @Test
    @DisplayName("여러 상품 ID를 전달하면 병렬로 조회하여 모든 결과를 반환한다")
    void getProductInfos_multipleIds_returnsAllResults() {
        UUID productId1 = UUID.randomUUID();
        UUID productId2 = UUID.randomUUID();

        given(restClient.get()).willReturn(uriSpec);
        stubProduct(productId1, new ProductDetailResponse(productId1.toString(), "Product 1", "desc", "ON_SALE", 10000, UUID.randomUUID().toString()));
        stubProduct(productId2, new ProductDetailResponse(productId2.toString(), "Product 2", "desc", "ON_SALE", 20000, UUID.randomUUID().toString()));

        Map<UUID, ProductInfo> result = provider.getProductInfos(Set.of(productId1, productId2));

        assertThat(result).hasSize(2);
        assertThat(result.get(productId1).name()).isEqualTo("Product 1");
        assertThat(result.get(productId1).price()).isEqualTo(10000);
        assertThat(result.get(productId2).name()).isEqualTo("Product 2");
        assertThat(result.get(productId2).price()).isEqualTo(20000);
    }

    @Test
    @DisplayName("빈 상품 ID 집합을 전달하면 빈 맵을 반환하고 HTTP 호출을 하지 않는다")
    void getProductInfos_emptyIds_returnsEmptyMapWithoutHttpCall() {
        Map<UUID, ProductInfo> result = provider.getProductInfos(Set.of());

        assertThat(result).isEmpty();
        verifyNoInteractions(restClient);
    }

    @Test
    @DisplayName("상품 조회 실패 시 DELETED 상태로 반환한다")
    void getProductInfos_fetchFails_returnsDELETED() {
        UUID productId = UUID.randomUUID();

        given(restClient.get()).willReturn(uriSpec);
        stubProductFailure(productId, new RestClientException("Connection refused"));

        Map<UUID, ProductInfo> result = provider.getProductInfos(Set.of(productId));

        assertThat(result).hasSize(1);
        assertThat(result.get(productId).status()).isEqualTo("DELETED");
        assertThat(result.get(productId).name()).isNull();
    }

    @Test
    @DisplayName("응답이 null이면 DELETED 상태로 반환한다")
    void getProductInfos_nullResponse_returnsDELETED() {
        UUID productId = UUID.randomUUID();

        given(restClient.get()).willReturn(uriSpec);
        stubProduct(productId, null);

        Map<UUID, ProductInfo> result = provider.getProductInfos(Set.of(productId));

        assertThat(result).hasSize(1);
        assertThat(result.get(productId).status()).isEqualTo("DELETED");
        assertThat(result.get(productId).name()).isNull();
    }

    @Test
    @DisplayName("일부 상품만 조회 실패하면 성공한 상품은 정상 반환하고 실패한 상품은 DELETED로 반환한다")
    void getProductInfos_partialFailure_returnsPartialResults() {
        UUID successId = UUID.randomUUID();
        UUID failId = UUID.randomUUID();

        given(restClient.get()).willReturn(uriSpec);
        stubProduct(successId, new ProductDetailResponse(successId.toString(), "Success Product", "desc", "ON_SALE", 15000, UUID.randomUUID().toString()));
        stubProductFailure(failId, new RestClientException("Not found"));

        Map<UUID, ProductInfo> result = provider.getProductInfos(Set.of(successId, failId));

        assertThat(result).hasSize(2);
        assertThat(result.get(successId).name()).isEqualTo("Success Product");
        assertThat(result.get(successId).status()).isEqualTo("ON_SALE");
        assertThat(result.get(failId).status()).isEqualTo("DELETED");
        assertThat(result.get(failId).name()).isNull();
    }
}
