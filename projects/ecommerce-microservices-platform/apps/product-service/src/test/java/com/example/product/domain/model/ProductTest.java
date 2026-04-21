package com.example.product.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Product 애그리게이트 테스트")
class ProductTest {

    private ProductVariant createVariant() {
        return ProductVariant.create("기본", new StockQuantity(10), new Price(0));
    }

    @Test
    @DisplayName("유효한 값으로 상품을 생성할 수 있다")
    void create_validInput_success() {
        ProductVariant variant = createVariant();
        Product product = Product.create("테스트 상품", "설명", new Price(10000), null, List.of(variant));

        assertThat(product.getId()).isNotNull();
        assertThat(product.getName()).isEqualTo("테스트 상품");
        assertThat(product.getDescription()).isEqualTo("설명");
        assertThat(product.getPrice()).isEqualTo(new Price(10000));
        assertThat(product.getStatus()).isEqualTo(ProductStatus.ON_SALE);
        assertThat(product.getVariants()).hasSize(1);
        assertThat(product.getCreatedAt()).isNotNull();
        assertThat(product.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("카테고리와 함께 상품을 생성할 수 있다")
    void create_withCategory_success() {
        UUID categoryId = UUID.randomUUID();
        Product product = Product.create("상품", "설명", new Price(5000), categoryId, List.of(createVariant()));

        assertThat(product.getCategoryId()).isEqualTo(categoryId);
    }

    @Test
    @DisplayName("상품 이름이 비어있으면 예외가 발생한다")
    void create_blankName_throws() {
        assertThatThrownBy(() -> Product.create("", "설명", new Price(10000), null, List.of(createVariant())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Product name must not be blank");
    }

    @Test
    @DisplayName("상품 이름이 null이면 예외가 발생한다")
    void create_nullName_throws() {
        assertThatThrownBy(() -> Product.create(null, "설명", new Price(10000), null, List.of(createVariant())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Product name must not be blank");
    }

    @Test
    @DisplayName("상품 이름이 255자를 초과하면 예외가 발생한다")
    void create_nameTooLong_throws() {
        String longName = "a".repeat(256);
        assertThatThrownBy(() -> Product.create(longName, "설명", new Price(10000), null, List.of(createVariant())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Product name must not exceed 255 characters");
    }

    @Test
    @DisplayName("가격이 null이면 예외가 발생한다")
    void create_nullPrice_throws() {
        assertThatThrownBy(() -> Product.create("상품", "설명", null, null, List.of(createVariant())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Price must not be null");
    }

    @Test
    @DisplayName("variant 없이 상품을 생성하면 예외가 발생한다")
    void create_emptyVariants_throws() {
        assertThatThrownBy(() -> Product.create("상품", "설명", new Price(10000), null, Collections.emptyList()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Product must have at least one variant");
    }

    @Test
    @DisplayName("variant가 null이면 예외가 발생한다")
    void create_nullVariants_throws() {
        assertThatThrownBy(() -> Product.create("상품", "설명", new Price(10000), null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Product must have at least one variant");
    }

    @Test
    @DisplayName("리스트 내 null variant가 있으면 예외가 발생한다")
    void create_nullElementInVariants_throws() {
        assertThatThrownBy(() -> Product.create("상품", "설명", new Price(10000), null, Collections.singletonList(null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Variant must not be null");
    }

    @Test
    @DisplayName("variant를 추가할 수 있다")
    void addVariant_valid_success() {
        Product product = Product.create("상품", "설명", new Price(10000), null, List.of(createVariant()));
        ProductVariant newVariant = ProductVariant.create("추가 옵션", new StockQuantity(5), new Price(1000));

        product.addVariant(newVariant);

        assertThat(product.getVariants()).hasSize(2);
    }

    @Test
    @DisplayName("null variant를 추가하면 예외가 발생한다")
    void addVariant_null_throws() {
        Product product = Product.create("상품", "설명", new Price(10000), null, List.of(createVariant()));

        assertThatThrownBy(() -> product.addVariant(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Variant must not be null");
    }

    @Test
    @DisplayName("상품 이름을 변경할 수 있다")
    void updateName_valid_success() {
        Product product = Product.create("상품", "설명", new Price(10000), null, List.of(createVariant()));
        product.updateName("새 상품명");

        assertThat(product.getName()).isEqualTo("새 상품명");
    }

    @Test
    @DisplayName("상품 가격을 변경할 수 있다")
    void updatePrice_valid_success() {
        Product product = Product.create("상품", "설명", new Price(10000), null, List.of(createVariant()));
        product.updatePrice(new Price(20000));

        assertThat(product.getPrice()).isEqualTo(new Price(20000));
    }

    @Test
    @DisplayName("상품 상태를 변경할 수 있다")
    void changeStatus_valid_success() {
        Product product = Product.create("상품", "설명", new Price(10000), null, List.of(createVariant()));
        product.changeStatus(ProductStatus.SOLD_OUT);

        assertThat(product.getStatus()).isEqualTo(ProductStatus.SOLD_OUT);
    }

    @Test
    @DisplayName("null 상태로 변경하면 예외가 발생한다")
    void changeStatus_null_throws() {
        Product product = Product.create("상품", "설명", new Price(10000), null, List.of(createVariant()));

        assertThatThrownBy(() -> product.changeStatus(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Status must not be null");
    }

    @Test
    @DisplayName("생성 시 기본 상태는 ON_SALE이다")
    void create_defaultStatus_isOnSale() {
        Product product = Product.create("상품", "설명", new Price(10000), null, List.of(createVariant()));
        assertThat(product.getStatus()).isEqualTo(ProductStatus.ON_SALE);
    }

    @Test
    @DisplayName("variant의 productId가 product의 id와 일치한다")
    void create_variantProductId_matchesProductId() {
        ProductVariant variant = createVariant();
        Product product = Product.create("상품", "설명", new Price(10000), null, List.of(variant));

        assertThat(variant.getProductId()).isEqualTo(product.getId());
    }

    @Test
    @DisplayName("addVariant 후 variant의 productId가 product의 id와 일치한다")
    void addVariant_assignsProductId() {
        Product product = Product.create("상품", "설명", new Price(10000), null, List.of(createVariant()));
        ProductVariant newVariant = ProductVariant.create("추가 옵션", new StockQuantity(5), new Price(1000));

        product.addVariant(newVariant);

        assertThat(newVariant.getProductId()).isEqualTo(product.getId());
    }

    @Test
    @DisplayName("description을 빈 문자열로 업데이트하면 null로 정규화된다")
    void updateDescription_blank_normalizesToNull() {
        Product product = Product.create("상품", "설명", new Price(10000), null, List.of(createVariant()));

        product.updateDescription("   ");

        assertThat(product.getDescription()).isNull();
    }

    @Test
    @DisplayName("description을 null로 업데이트할 수 있다")
    void updateDescription_null_setsNull() {
        Product product = Product.create("상품", "설명", new Price(10000), null, List.of(createVariant()));

        product.updateDescription(null);

        assertThat(product.getDescription()).isNull();
    }
}
