package com.example.product.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ProductVariant 엔티티 테스트")
class ProductVariantTest {

    @Test
    @DisplayName("유효한 값으로 variant를 생성할 수 있다")
    void create_validInput_success() {
        ProductVariant variant = ProductVariant.create("S", new StockQuantity(10), new Price(1000));

        assertThat(variant.getId()).isNotNull();
        assertThat(variant.getOptionName()).isEqualTo("S");
        assertThat(variant.getStock()).isEqualTo(new StockQuantity(10));
        assertThat(variant.getAdditionalPrice()).isEqualTo(new Price(1000));
    }

    @Test
    @DisplayName("optionName 앞뒤 공백이 제거된다")
    void create_trimmedOptionName_success() {
        ProductVariant variant = ProductVariant.create("  M  ", new StockQuantity(5), new Price(0));
        assertThat(variant.getOptionName()).isEqualTo("M");
    }

    @Test
    @DisplayName("optionName이 비어있으면 예외가 발생한다")
    void create_blankOptionName_throws() {
        assertThatThrownBy(() -> ProductVariant.create("", new StockQuantity(5), new Price(0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Option name must not be blank");
    }

    @Test
    @DisplayName("optionName이 null이면 예외가 발생한다")
    void create_nullOptionName_throws() {
        assertThatThrownBy(() -> ProductVariant.create(null, new StockQuantity(5), new Price(0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Option name must not be blank");
    }

    @Test
    @DisplayName("optionName이 100자를 초과하면 예외가 발생한다")
    void create_optionNameTooLong_throws() {
        String longName = "a".repeat(101);
        assertThatThrownBy(() -> ProductVariant.create(longName, new StockQuantity(5), new Price(0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Option name must not exceed 100 characters");
    }

    @Test
    @DisplayName("stock이 null이면 예외가 발생한다")
    void create_nullStock_throws() {
        assertThatThrownBy(() -> ProductVariant.create("S", null, new Price(0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Stock must not be null");
    }

    @Test
    @DisplayName("additionalPrice가 null이면 예외가 발생한다")
    void create_nullAdditionalPrice_throws() {
        assertThatThrownBy(() -> ProductVariant.create("S", new StockQuantity(5), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Additional price must not be null");
    }

    @Test
    @DisplayName("getStock()은 StockQuantity VO를 반환한다")
    void getStock_returnsVO() {
        ProductVariant variant = ProductVariant.create("S", new StockQuantity(7), new Price(0));
        assertThat(variant.getStock().value()).isEqualTo(7);
    }

    @Test
    @DisplayName("getAdditionalPrice()는 Price VO를 반환한다")
    void getAdditionalPrice_returnsVO() {
        ProductVariant variant = ProductVariant.create("S", new StockQuantity(5), new Price(2000));
        assertThat(variant.getAdditionalPrice().value()).isEqualTo(2000);
    }

    @Test
    @DisplayName("이미 상품에 할당된 variant를 다른 상품에 추가하면 예외가 발생한다")
    void assignProduct_alreadyAssigned_throws() {
        ProductVariant variant = ProductVariant.create("S", new StockQuantity(5), new Price(0));
        Product.create("상품A", "설명", new Price(10000), null, List.of(variant));

        assertThatThrownBy(() -> Product.create("상품B", "설명", new Price(20000), null, List.of(variant)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Variant is already assigned to a product");
    }
}
