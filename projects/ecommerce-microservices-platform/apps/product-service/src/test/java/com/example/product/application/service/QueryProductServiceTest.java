package com.example.product.application.service;

import com.example.product.application.dto.ProductDetail;
import com.example.product.application.dto.ProductListResult;
import com.example.product.application.dto.ProductSummary;
import com.example.product.application.port.ProductQueryPort;
import com.example.product.domain.exception.ProductNotFoundException;
import com.example.product.domain.model.Price;
import com.example.product.domain.model.Product;
import com.example.product.domain.model.ProductStatus;
import com.example.product.domain.model.ProductVariant;
import com.example.product.domain.model.StockQuantity;
import com.example.product.domain.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("QueryProductService 단위 테스트")
class QueryProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductQueryPort productQueryPort;

    @InjectMocks
    private QueryProductService queryProductService;

    @Test
    @DisplayName("목록 조회 성공 - 페이지네이션 결과 반환")
    void findAll_success_returnsPaginatedList() {
        UUID id = UUID.randomUUID();
        ProductSummary summary = new ProductSummary(id, "테스트 상품", ProductStatus.ON_SALE, 10000L, null);
        given(productQueryPort.findSummaries(any(), any(), anyInt(), anyInt()))
                .willReturn(new ProductListResult(List.of(summary), 0, 20, 1));

        ProductListResult result = queryProductService.findAll(null, null, 0, 20);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).id()).isEqualTo(id);
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.page()).isEqualTo(0);
        assertThat(result.size()).isEqualTo(20);
    }

    @Test
    @DisplayName("목록 조회 - categoryId 필터 적용")
    void findAll_withCategoryFilter_passesFilterToPort() {
        UUID categoryId = UUID.randomUUID();
        given(productQueryPort.findSummaries(eq(categoryId), any(), anyInt(), anyInt()))
                .willReturn(new ProductListResult(List.of(), 0, 20, 0));

        ProductListResult result = queryProductService.findAll(categoryId, null, 0, 20);

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isEqualTo(0);
    }

    @Test
    @DisplayName("상세 조회 성공 - variants 포함 반환")
    void findById_success_returnsDetailWithVariants() {
        ProductVariant variant = ProductVariant.create("기본", new StockQuantity(10), new Price(0));
        Product product = Product.create("테스트 상품", "설명", new Price(15000), null, List.of(variant));
        given(productRepository.findById(product.getId())).willReturn(Optional.of(product));

        ProductDetail detail = queryProductService.findById(product.getId());

        assertThat(detail.id()).isEqualTo(product.getId());
        assertThat(detail.name()).isEqualTo("테스트 상품");
        assertThat(detail.variants()).hasSize(1);
        assertThat(detail.variants().get(0).optionName()).isEqualTo("기본");
        assertThat(detail.variants().get(0).stock()).isEqualTo(10);
    }

    @Test
    @DisplayName("존재하지 않는 상품 조회 시 ProductNotFoundException 발생")
    void findById_notFound_throwsException() {
        UUID unknownId = UUID.randomUUID();
        given(productRepository.findById(unknownId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> queryProductService.findById(unknownId))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining(unknownId.toString());
    }

    @Test
    @DisplayName("soft-delete된 상품 조회 시 ProductNotFoundException 발생")
    void findById_softDeleted_throwsException() {
        UUID deletedProductId = UUID.randomUUID();
        given(productRepository.findById(deletedProductId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> queryProductService.findById(deletedProductId))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining(deletedProductId.toString());
    }
}
