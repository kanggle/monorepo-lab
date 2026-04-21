package com.example.product.domain.model;

import com.example.product.domain.exception.InsufficientStockException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Inventory 애그리게이트 테스트")
class InventoryTest {

    @Test
    @DisplayName("유효한 값으로 Inventory를 생성할 수 있다")
    void create_validInput_success() {
        UUID variantId = UUID.randomUUID();
        Inventory inventory = Inventory.create(variantId, new StockQuantity(10));

        assertThat(inventory.getVariantId()).isEqualTo(variantId);
        assertThat(inventory.currentStock()).isEqualTo(new StockQuantity(10));
    }

    @Test
    @DisplayName("null variantId로 생성하면 예외가 발생한다")
    void create_nullVariantId_throws() {
        assertThatThrownBy(() -> Inventory.create(null, new StockQuantity(10)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("variantId must not be null");
    }

    @Test
    @DisplayName("null stock으로 생성하면 예외가 발생한다")
    void create_nullStock_throws() {
        assertThatThrownBy(() -> Inventory.create(UUID.randomUUID(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("stock must not be null");
    }

    @Test
    @DisplayName("재고를 증가시킬 수 있다")
    void increase_validAmount_increases() {
        Inventory inventory = Inventory.create(UUID.randomUUID(), new StockQuantity(10));

        inventory.increase(5);

        assertThat(inventory.currentStock()).isEqualTo(new StockQuantity(15));
    }

    @Test
    @DisplayName("재고를 감소시킬 수 있다")
    void decrease_validAmount_decreases() {
        Inventory inventory = Inventory.create(UUID.randomUUID(), new StockQuantity(10));

        inventory.decrease(3);

        assertThat(inventory.currentStock()).isEqualTo(new StockQuantity(7));
    }

    @Test
    @DisplayName("재고가 음수가 되면 InsufficientStockException이 발생한다")
    void decrease_resultNegative_throws() {
        Inventory inventory = Inventory.create(UUID.randomUUID(), new StockQuantity(5));

        assertThatThrownBy(() -> inventory.decrease(6))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("Cannot subtract 6 from stock 5");
    }

    @Test
    @DisplayName("재고를 0으로 만들 수 있다")
    void decrease_toZero_success() {
        Inventory inventory = Inventory.create(UUID.randomUUID(), new StockQuantity(5));

        inventory.decrease(5);

        assertThat(inventory.currentStock()).isEqualTo(new StockQuantity(0));
    }

    @Test
    @DisplayName("adjustStock - 양수 delta로 재고가 증가한다")
    void adjustStock_positiveDelta_increases() {
        Inventory inventory = Inventory.create(UUID.randomUUID(), new StockQuantity(10));

        inventory.adjustStock(5);

        assertThat(inventory.currentStock()).isEqualTo(new StockQuantity(15));
    }

    @Test
    @DisplayName("adjustStock - 음수 delta로 재고가 감소한다")
    void adjustStock_negativeDelta_decreases() {
        Inventory inventory = Inventory.create(UUID.randomUUID(), new StockQuantity(10));

        inventory.adjustStock(-3);

        assertThat(inventory.currentStock()).isEqualTo(new StockQuantity(7));
    }

    @Test
    @DisplayName("adjustStock - 음수 결과가 되면 InsufficientStockException이 발생한다")
    void adjustStock_resultNegative_throws() {
        Inventory inventory = Inventory.create(UUID.randomUUID(), new StockQuantity(5));

        assertThatThrownBy(() -> inventory.adjustStock(-6))
                .isInstanceOf(InsufficientStockException.class);
    }
}
