package com.example.product.domain.model;

import com.example.product.domain.exception.InsufficientStockException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("StockQuantity 값 객체 테스트")
class StockQuantityTest {

    @Test
    @DisplayName("유효한 수량으로 생성할 수 있다")
    void create_validValue_success() {
        StockQuantity stock = new StockQuantity(100);
        assertThat(stock.value()).isEqualTo(100);
    }

    @Test
    @DisplayName("0으로 생성할 수 있다")
    void create_zero_success() {
        StockQuantity stock = new StockQuantity(0);
        assertThat(stock.value()).isZero();
    }

    @Test
    @DisplayName("음수 수량은 생성할 수 없다")
    void create_negativeValue_throws() {
        assertThatThrownBy(() -> new StockQuantity(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Stock cannot be negative");
    }

    @Test
    @DisplayName("수량을 더할 수 있다")
    void add_validAmount_returnsNewStock() {
        StockQuantity stock = new StockQuantity(10);
        StockQuantity result = stock.add(new StockQuantity(5));
        assertThat(result.value()).isEqualTo(15);
    }

    @Test
    @DisplayName("수량을 차감할 수 있다")
    void subtract_validAmount_returnsNewStock() {
        StockQuantity stock = new StockQuantity(10);
        StockQuantity result = stock.subtract(new StockQuantity(5));
        assertThat(result.value()).isEqualTo(5);
    }

    @Test
    @DisplayName("차감 결과가 음수이면 InsufficientStockException이 발생한다")
    void subtract_resultNegative_throws() {
        StockQuantity stock = new StockQuantity(5);
        assertThatThrownBy(() -> stock.subtract(new StockQuantity(6)))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("Cannot subtract 6 from stock 5");
    }
}
