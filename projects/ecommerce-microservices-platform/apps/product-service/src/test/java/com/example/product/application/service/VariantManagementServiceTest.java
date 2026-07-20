package com.example.product.application.service;

import com.example.product.application.dto.VariantDetail;
import com.example.product.domain.exception.DuplicateVariantOptionException;
import com.example.product.domain.exception.ProductNotFoundException;
import com.example.product.domain.model.Price;
import com.example.product.domain.model.Product;
import com.example.product.domain.model.ProductVariant;
import com.example.product.domain.model.StockQuantity;
import com.example.product.domain.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("VariantManagementService 단위 테스트")
class VariantManagementServiceTest {

    @Mock
    private ProductRepository productRepository;

    private VariantManagementService variantManagementService;

    private Product existingProduct;

    @BeforeEach
    void setUp() {
        variantManagementService = new VariantManagementService(productRepository);

        existingProduct = Product.create(
                "테스트 상품", "설명", new Price(10000L), null,
                List.of(ProductVariant.create("기본", new StockQuantity(10), new Price(0L))));
    }

    @Nested
    @DisplayName("addVariant")
    class AddVariant {

        @Test
        @DisplayName("바리언트 추가 성공 시 VariantDetail을 반환한다")
        void addVariant_success_returnsVariantDetail() {
            UUID productId = existingProduct.getId();
            given(productRepository.findById(productId)).willReturn(Optional.of(existingProduct));
            given(productRepository.saveAndFlush(any(Product.class))).willAnswer(inv -> inv.getArgument(0));

            VariantDetail result = variantManagementService.addVariant(productId, "레드", 5, 2000L);

            assertThat(result).isNotNull();
            assertThat(result.id()).isNotNull();
            assertThat(result.optionName()).isEqualTo("레드");
            assertThat(result.stock()).isEqualTo(5);
            assertThat(result.additionalPrice()).isEqualTo(2000L);
            verify(productRepository).saveAndFlush(existingProduct);
        }

        @Test
        @DisplayName("바리언트 추가 후 상품에 바리언트가 증가한다")
        void addVariant_success_productHasMoreVariants() {
            UUID productId = existingProduct.getId();
            given(productRepository.findById(productId)).willReturn(Optional.of(existingProduct));
            given(productRepository.saveAndFlush(any(Product.class))).willAnswer(inv -> inv.getArgument(0));

            int beforeCount = existingProduct.getVariants().size();
            variantManagementService.addVariant(productId, "블루", 3, 1000L);

            assertThat(existingProduct.getVariants()).hasSize(beforeCount + 1);
        }

        @Test
        @DisplayName("존재하지 않는 상품에 바리언트 추가 시 ProductNotFoundException 발생")
        void addVariant_productNotFound_throws() {
            UUID unknownId = UUID.randomUUID();
            given(productRepository.findById(unknownId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> variantManagementService.addVariant(unknownId, "레드", 5, 2000L))
                    .isInstanceOf(ProductNotFoundException.class);

            verify(productRepository, never()).save(any());
        }

        @Test
        @DisplayName("옵션 이름이 빈 문자열이면 IllegalArgumentException 발생")
        void addVariant_blankOptionName_throws() {
            UUID productId = existingProduct.getId();
            given(productRepository.findById(productId)).willReturn(Optional.of(existingProduct));

            assertThatThrownBy(() -> variantManagementService.addVariant(productId, "", 5, 2000L))
                    .isInstanceOf(IllegalArgumentException.class);

            verify(productRepository, never()).save(any());
        }

        @Test
        @DisplayName("재고가 음수이면 IllegalArgumentException 발생")
        void addVariant_negativeStock_throws() {
            UUID productId = existingProduct.getId();
            given(productRepository.findById(productId)).willReturn(Optional.of(existingProduct));

            assertThatThrownBy(() -> variantManagementService.addVariant(productId, "레드", -1, 2000L))
                    .isInstanceOf(IllegalArgumentException.class);

            verify(productRepository, never()).save(any());
        }

        @Test
        @DisplayName("TASK-BE-536 AC-0/AC-4 동일 productId+optionName 유니크 제약 위반 → DuplicateVariantOptionException, 이중 재고 없음")
        void addVariant_duplicateOptionName_translatesConstraintViolation() {
            UUID productId = existingProduct.getId();
            given(productRepository.findById(productId)).willReturn(Optional.of(existingProduct));
            given(productRepository.saveAndFlush(any(Product.class)))
                    .willThrow(new DataIntegrityViolationException("uq_product_variants_option"));

            assertThatThrownBy(() -> variantManagementService.addVariant(productId, "기본", 5, 2000L))
                    .isInstanceOf(DuplicateVariantOptionException.class);
        }
    }

    @Nested
    @DisplayName("updateVariant")
    class UpdateVariant {

        @Test
        @DisplayName("바리언트 수정 성공 시 수정된 VariantDetail을 반환한다")
        void updateVariant_success_returnsUpdatedDetail() {
            UUID productId = existingProduct.getId();
            UUID variantId = existingProduct.getVariants().get(0).getId();
            given(productRepository.findById(productId)).willReturn(Optional.of(existingProduct));
            given(productRepository.save(any(Product.class))).willAnswer(inv -> inv.getArgument(0));

            VariantDetail result = variantManagementService.updateVariant(productId, variantId, "변경됨", 5000L);

            assertThat(result.optionName()).isEqualTo("변경됨");
            assertThat(result.additionalPrice()).isEqualTo(5000L);
            verify(productRepository).save(existingProduct);
        }

        @Test
        @DisplayName("존재하지 않는 상품의 바리언트 수정 시 ProductNotFoundException 발생")
        void updateVariant_productNotFound_throws() {
            UUID unknownId = UUID.randomUUID();
            given(productRepository.findById(unknownId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> variantManagementService.updateVariant(
                    unknownId, UUID.randomUUID(), "변경됨", 5000L))
                    .isInstanceOf(ProductNotFoundException.class);

            verify(productRepository, never()).save(any());
        }

        @Test
        @DisplayName("존재하지 않는 바리언트 수정 시 IllegalArgumentException 발생")
        void updateVariant_variantNotFound_throws() {
            UUID productId = existingProduct.getId();
            UUID unknownVariantId = UUID.randomUUID();
            given(productRepository.findById(productId)).willReturn(Optional.of(existingProduct));

            assertThatThrownBy(() -> variantManagementService.updateVariant(
                    productId, unknownVariantId, "변경됨", 5000L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Variant not found");

            verify(productRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("removeVariant")
    class RemoveVariant {

        private Product productWithTwoVariants;

        @BeforeEach
        void setUp() {
            productWithTwoVariants = Product.create(
                    "다중 바리언트 상품", "설명", new Price(20000L), null,
                    List.of(
                            ProductVariant.create("기본", new StockQuantity(10), new Price(0L)),
                            ProductVariant.create("프리미엄", new StockQuantity(5), new Price(5000L))));
        }

        @Test
        @DisplayName("바리언트 삭제 성공 시 상품에서 제거된다")
        void removeVariant_success_variantRemoved() {
            UUID productId = productWithTwoVariants.getId();
            UUID variantIdToRemove = productWithTwoVariants.getVariants().get(1).getId();
            given(productRepository.findById(productId)).willReturn(Optional.of(productWithTwoVariants));
            given(productRepository.save(any(Product.class))).willAnswer(inv -> inv.getArgument(0));

            variantManagementService.removeVariant(productId, variantIdToRemove);

            assertThat(productWithTwoVariants.getVariants()).hasSize(1);
            verify(productRepository).save(productWithTwoVariants);
        }

        @Test
        @DisplayName("마지막 바리언트 삭제 시 IllegalStateException 발생")
        void removeVariant_lastVariant_throws() {
            UUID productId = existingProduct.getId();
            UUID onlyVariantId = existingProduct.getVariants().get(0).getId();
            given(productRepository.findById(productId)).willReturn(Optional.of(existingProduct));

            assertThatThrownBy(() -> variantManagementService.removeVariant(productId, onlyVariantId))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("at least one variant");

            verify(productRepository, never()).save(any());
        }

        @Test
        @DisplayName("존재하지 않는 상품의 바리언트 삭제 시 ProductNotFoundException 발생")
        void removeVariant_productNotFound_throws() {
            UUID unknownId = UUID.randomUUID();
            given(productRepository.findById(unknownId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> variantManagementService.removeVariant(unknownId, UUID.randomUUID()))
                    .isInstanceOf(ProductNotFoundException.class);

            verify(productRepository, never()).save(any());
        }

        @Test
        @DisplayName("존재하지 않는 바리언트 삭제 시 IllegalArgumentException 발생")
        void removeVariant_variantNotFound_throws() {
            UUID productId = productWithTwoVariants.getId();
            UUID unknownVariantId = UUID.randomUUID();
            given(productRepository.findById(productId)).willReturn(Optional.of(productWithTwoVariants));

            assertThatThrownBy(() -> variantManagementService.removeVariant(productId, unknownVariantId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Variant not found");

            verify(productRepository, never()).save(any());
        }
    }
}
