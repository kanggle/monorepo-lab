package com.example.order.domain.model;

import com.example.order.domain.exception.InvalidOrderException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Order 쿠폰 할인 불변식 (TASK-INT-026)")
class OrderCouponDiscountTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-09-15T00:00:00Z"), ZoneOffset.UTC);

    /** subtotal = 1×20,000 + 2×5,000 = 30,000 */
    private Order newOrder() {
        return Order.create("user-1",
                List.of(
                        new Order.OrderItemData("p1", "v1", "노트북", null, 1, 20000L),
                        new Order.OrderItemData("p2", "v2", "마우스", null, 2, 5000L)),
                new ShippingAddress("홍길동", "010-1234-5678", "12345", "서울시 강남구", "101호"),
                clock);
    }

    @Test
    @DisplayName("할인을 적용하면 totalPrice 는 결제할 금액(소계 − 할인)이 되고 소계는 그대로다")
    void applyCouponDiscount_setsPayableTotal() {
        Order order = newOrder();

        order.applyCouponDiscount("coupon-1", 5000L);

        assertThat(order.getTotalPrice()).isEqualTo(25000L);
        assertThat(order.getDiscountAmount()).isEqualTo(5000L);
        assertThat(order.getCouponId()).isEqualTo("coupon-1");
        assertThat(order.subtotal()).isEqualTo(30000L);
    }

    @Test
    @DisplayName("쿠폰 없는 주문은 할인 0, 쿠폰 없음, totalPrice = 소계")
    void noCoupon_defaults() {
        Order order = newOrder();

        assertThat(order.getDiscountAmount()).isZero();
        assertThat(order.getCouponId()).isNull();
        assertThat(order.getTotalPrice()).isEqualTo(order.subtotal());
    }

    @Test
    @DisplayName("🔴 할인 후 1원 미만이 되면 거부하고 주문은 바뀌지 않는다 — PG 는 0원을 청구하지 못한다")
    void discountLeavingNothingToPay_isRejected_andOrderUnchanged() {
        Order order = newOrder();

        assertThatThrownBy(() -> order.applyCouponDiscount("coupon-1", 30000L))
                .isInstanceOf(InvalidOrderException.class);
        assertThat(order.getTotalPrice()).isEqualTo(30000L);
        assertThat(order.getCouponId()).isNull();
        assertThat(order.getDiscountAmount()).isZero();
    }

    @Test
    @DisplayName("음수 할인은 거부한다")
    void negativeDiscount_isRejected() {
        Order order = newOrder();

        assertThatThrownBy(() -> order.applyCouponDiscount("coupon-1", -1L))
                .isInstanceOf(InvalidOrderException.class);
    }

    @Test
    @DisplayName("한 주문에 쿠폰은 한 번만")
    void secondCoupon_isRejected() {
        Order order = newOrder();
        order.applyCouponDiscount("coupon-1", 5000L);

        assertThatThrownBy(() -> order.applyCouponDiscount("coupon-2", 1000L))
                .isInstanceOf(InvalidOrderException.class);
        assertThat(order.getTotalPrice()).isEqualTo(25000L);
    }

    @Test
    @DisplayName("빈 쿠폰 id 는 거부한다")
    void blankCouponId_isRejected() {
        Order order = newOrder();

        assertThatThrownBy(() -> order.applyCouponDiscount(" ", 1000L))
                .isInstanceOf(InvalidOrderException.class);
    }

    @Test
    @DisplayName("PENDING 이 아닌 주문에는 적용하지 않는다")
    void nonPendingOrder_isRejected() {
        Order order = newOrder();
        order.cancel(clock);

        assertThatThrownBy(() -> order.applyCouponDiscount("coupon-1", 1000L))
                .isInstanceOf(InvalidOrderException.class);
    }

    @Test
    @DisplayName("reconstitute 는 쿠폰 스냅샷을 복원한다")
    void reconstitute_restoresCouponSnapshot() {
        Instant now = Instant.now(clock);
        Order order = Order.reconstitute("order-1", "user-1", List.of(), OrderStatus.PENDING, 25000L,
                new ShippingAddress("홍길동", "010-1234-5678", "12345", "서울시 강남구", null),
                now, now, null, null, null, 0, null, 0L, "coupon-1", 5000L);

        assertThat(order.getCouponId()).isEqualTo("coupon-1");
        assertThat(order.getDiscountAmount()).isEqualTo(5000L);
        assertThat(order.getTotalPrice()).isEqualTo(25000L);
    }
}
