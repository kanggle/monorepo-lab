package com.example.product.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Price 값 객체 테스트")
class PriceTest {

    @Test
    @DisplayName("유효한 가격으로 생성할 수 있다")
    void create_validValue_success() {
        Price price = new Price(10000);
        assertThat(price.value()).isEqualTo(10000);
    }

    @Test
    @DisplayName("0원으로 생성할 수 있다")
    void create_zero_success() {
        Price price = new Price(0);
        assertThat(price.value()).isZero();
    }

    @Test
    @DisplayName("음수 가격은 생성할 수 없다")
    void create_negativeValue_throws() {
        assertThatThrownBy(() -> new Price(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Price cannot be negative");
    }

    @Test
    @DisplayName("가격을 합산할 수 있다")
    void add_twoPrice_returnsSum() {
        Price base = new Price(10000);
        Price additional = new Price(2000);
        Price result = base.add(additional);
        assertThat(result.value()).isEqualTo(12000);
    }

    @Test
    @DisplayName("가격을 차감할 수 있다")
    void subtract_validAmount_returnsResult() {
        Price base = new Price(10000);
        Price result = base.subtract(new Price(3000));
        assertThat(result.value()).isEqualTo(7000);
    }

    @Test
    @DisplayName("차감 결과가 음수이면 예외가 발생한다")
    void subtract_resultNegative_throws() {
        Price base = new Price(3000);
        assertThatThrownBy(() -> base.subtract(new Price(5000)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot subtract 5000 from 3000");
    }

    @Test
    @DisplayName("동일한 금액을 차감하면 0이 된다")
    void subtract_sameAmount_returnsZero() {
        Price base = new Price(5000);
        assertThat(base.subtract(new Price(5000)).value()).isZero();
    }
}
