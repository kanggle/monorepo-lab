package com.example.order.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("OrderItem 도메인 값 객체 단위 테스트")
class OrderItemTest {

    @Test
    @DisplayName("유효한 값으로 생성 시 모든 필드가 올바르게 설정된다")
    void create_validValues_setsAllFieldsCorrectly() {
        OrderItem item = new OrderItem("item-1", "product-1", "variant-1",
                "노트북", "블랙", 2, 1500000L);

        assertThat(item.getId()).isEqualTo("item-1");
        assertThat(item.getProductId()).isEqualTo("product-1");
        assertThat(item.getVariantId()).isEqualTo("variant-1");
        assertThat(item.getProductName()).isEqualTo("노트북");
        assertThat(item.getOptionName()).isEqualTo("블랙");
        assertThat(item.getQuantity()).isEqualTo(2);
        assertThat(item.getUnitPrice()).isEqualTo(1500000L);
    }

    @Test
    @DisplayName("quantity가 0이면 생성 시 IllegalArgumentException이 발생한다")
    void create_zeroQuantity_throwsIllegalArgumentException() {
        assertThatThrownBy(() ->
                new OrderItem("item-1", "product-1", "variant-1", "노트북", "블랙", 0, 1000L)
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quantity");
    }

    @Test
    @DisplayName("quantity가 음수이면 생성 시 IllegalArgumentException이 발생한다")
    void create_negativeQuantity_throwsIllegalArgumentException() {
        assertThatThrownBy(() ->
                new OrderItem("item-1", "product-1", "variant-1", "노트북", "블랙", -1, 1000L)
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quantity");
    }

    @Test
    @DisplayName("unitPrice가 0이면 생성 시 IllegalArgumentException이 발생한다")
    void create_zeroUnitPrice_throwsIllegalArgumentException() {
        assertThatThrownBy(() ->
                new OrderItem("item-1", "product-1", "variant-1", "노트북", "블랙", 1, 0L)
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unitPrice");
    }

    @Test
    @DisplayName("unitPrice가 음수이면 생성 시 IllegalArgumentException이 발생한다")
    void create_negativeUnitPrice_throwsIllegalArgumentException() {
        assertThatThrownBy(() ->
                new OrderItem("item-1", "product-1", "variant-1", "노트북", "블랙", 1, -1L)
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unitPrice");
    }

    @Test
    @DisplayName("subtotal은 unitPrice * quantity를 반환한다")
    void subtotal_multipleQuantity_returnsUnitPriceTimesQuantity() {
        OrderItem item = new OrderItem("item-1", "product-1", "variant-1",
                "노트북", "블랙", 3, 500000L);

        assertThat(item.subtotal()).isEqualTo(1500000L);
    }

    @Test
    @DisplayName("quantity가 1이면 subtotal은 unitPrice를 반환한다")
    void subtotal_quantityOne_returnsUnitPrice() {
        OrderItem item = new OrderItem("item-1", "product-1", "variant-1",
                "노트북", "블랙", 1, 800000L);

        assertThat(item.subtotal()).isEqualTo(800000L);
    }

    @Test
    @DisplayName("reconstitute는 검증 없이 quantity=0으로도 객체를 생성한다")
    void reconstitute_zeroQuantity_doesNotThrow() {
        OrderItem item = OrderItem.reconstitute("item-1", "product-1", "variant-1",
                "노트북", "블랙", 0, 1000L);

        assertThat(item.getQuantity()).isEqualTo(0);
        assertThat(item.getId()).isEqualTo("item-1");
    }
}
