package com.example.order.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Order-line seller capture unit test (ADR-MONO-030 §3.2 — order-line attribution).
 * Each line captures its own seller (single + multi-seller order); absent → default.
 */
@DisplayName("주문 라인 셀러 귀속 단위 테스트 (ADR-MONO-030 §3.2)")
class OrderItemSellerCaptureTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-13T00:00:00Z"), ZoneOffset.UTC);
    private static final ShippingAddress ADDR =
            new ShippingAddress("홍길동", "010-1234-5678", "12345", "서울시 강남구", "101호");

    @Test
    @DisplayName("라인이 자신의 seller_id 를 불변 캡처한다 (단일 셀러)")
    void singleSeller_captured() {
        Order order = Order.create("user-1",
                List.of(new Order.OrderItemData("p1", "v1", "노트북", "블랙", 1, 1000L, "seller-a1")),
                ADDR, CLOCK);

        assertThat(order.getItems()).singleElement()
                .extracting(OrderItem::getSellerId).isEqualTo("seller-a1");
    }

    @Test
    @DisplayName("한 주문의 라인들이 서로 다른 셀러에 독립 귀속된다 (다중 셀러)")
    void multiSeller_independentlyAttributed() {
        Order order = Order.create("user-1",
                List.of(
                        new Order.OrderItemData("p1", "v1", "노트북", null, 1, 1000L, "seller-a1"),
                        new Order.OrderItemData("p2", "v2", "마우스", null, 2, 500L, "seller-a2")
                ),
                ADDR, CLOCK);

        assertThat(order.getItems())
                .extracting(OrderItem::getSellerId)
                .containsExactly("seller-a1", "seller-a2");
    }

    @Test
    @DisplayName("seller 부재 라인은 default seller 로 귀속 (D8 degrade)")
    void absentSeller_defaultsToDefault() {
        Order order = Order.create("user-1",
                List.of(new Order.OrderItemData("p1", "v1", "노트북", null, 1, 1000L, null)),
                ADDR, CLOCK);

        assertThat(order.getItems()).singleElement()
                .extracting(OrderItem::getSellerId).isEqualTo(OrderItem.DEFAULT_SELLER_ID);
    }

    @Test
    @DisplayName("backward-compatible OrderItemData (seller 미지정) = default seller")
    void backwardCompatItemData_defaultsToDefault() {
        Order order = Order.create("user-1",
                List.of(new Order.OrderItemData("p1", "v1", "노트북", null, 1, 1000L)),
                ADDR, CLOCK);

        assertThat(order.getItems()).singleElement()
                .extracting(OrderItem::getSellerId).isEqualTo("default");
    }
}
