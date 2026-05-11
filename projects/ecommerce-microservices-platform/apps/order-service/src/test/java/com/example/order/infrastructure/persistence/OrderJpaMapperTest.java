package com.example.order.infrastructure.persistence;

import com.example.order.domain.model.Order;
import com.example.order.domain.model.OrderItem;
import com.example.order.domain.model.OrderStatus;
import com.example.order.domain.model.ShippingAddress;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OrderJpaMapper 단위 테스트")
class OrderJpaMapperTest {

    private final OrderJpaMapper mapper = new OrderJpaMapper();

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-03-25T10:00:00Z"), ZoneOffset.UTC);

    @Test
    @DisplayName("도메인 → JpaEntity 변환 시 모든 필드가 매핑된다")
    void toEntity_mapsAllFields() {
        ShippingAddress address = new ShippingAddress("홍길동", "010-1234-5678", "12345",
                "서울시 강남구", "101호");
        OrderItem item = OrderItem.reconstitute("item-1", "prod-1", "var-1",
                "상품A", "옵션1", 2, 10000L);
        Instant now = Instant.parse("2025-06-01T10:00:00Z");
        Order order = Order.reconstitute("order-1", "user-1", List.of(item),
                OrderStatus.CONFIRMED, 20000L, address, now, now,
                "pay-1", now, null, 0, null, 1L);

        OrderJpaEntity entity = mapper.toEntity(order);

        assertThat(entity.getOrderId()).isEqualTo("order-1");
        assertThat(entity.getUserId()).isEqualTo("user-1");
        assertThat(entity.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(entity.getTotalPrice()).isEqualTo(20000L);
        assertThat(entity.getShippingAddress().getRecipient()).isEqualTo("홍길동");
        assertThat(entity.getCreatedAt()).isEqualTo(now);
        assertThat(entity.getUpdatedAt()).isEqualTo(now);
        assertThat(entity.getPaymentId()).isEqualTo("pay-1");
        assertThat(entity.getPaidAt()).isEqualTo(now);
        assertThat(entity.getRefundedAt()).isNull();
        assertThat(entity.getVersion()).isEqualTo(1L);
        assertThat(entity.getItems()).hasSize(1);
        assertThat(entity.getItems().get(0).getProductId()).isEqualTo("prod-1");
    }

    @Test
    @DisplayName("JpaEntity → 도메인 변환 시 모든 필드가 매핑된다")
    void toDomain_mapsAllFields() {
        ShippingAddress address = new ShippingAddress("홍길동", "010-1234-5678", "12345",
                "서울시 강남구", null);
        OrderItem item = OrderItem.reconstitute("item-1", "prod-1", "var-1",
                "상품A", null, 3, 5000L);
        Instant now = Instant.parse("2025-06-01T10:00:00Z");
        Order original = Order.reconstitute("order-1", "user-1", List.of(item),
                OrderStatus.PENDING, 15000L, address, now, now,
                null, null, null, 0, null, 0L);

        OrderJpaEntity entity = mapper.toEntity(original);
        Order restored = mapper.toDomain(entity);

        assertThat(restored.getOrderId()).isEqualTo("order-1");
        assertThat(restored.getUserId()).isEqualTo("user-1");
        assertThat(restored.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(restored.getTotalPrice()).isEqualTo(15000L);
        assertThat(restored.getShippingAddress().getRecipient()).isEqualTo("홍길동");
        assertThat(restored.getShippingAddress().getAddress2()).isNull();
        assertThat(restored.getPaymentId()).isNull();
        assertThat(restored.getVersion()).isEqualTo(0L);
        assertThat(restored.getItems()).hasSize(1);
        assertThat(restored.getItems().get(0).getId()).isEqualTo("item-1");
        assertThat(restored.getItems().get(0).getQuantity()).isEqualTo(3);
        assertThat(restored.getItems().get(0).getOptionName()).isNull();
    }

    @Test
    @DisplayName("도메인 → JpaEntity → 도메인 왕복 변환 시 데이터 손실이 없다")
    void roundTrip_noDataLoss() {
        ShippingAddress address = new ShippingAddress("김철수", "010-9876-5432", "54321",
                "부산시 해운대구", "202호");
        Order original = Order.create("user-1",
                List.of(new Order.OrderItemData("prod-1", "var-1", "상품A", "옵션1", 2, 10000L),
                        new Order.OrderItemData("prod-2", "var-2", "상품B", "옵션2", 1, 20000L)),
                address, FIXED_CLOCK);

        OrderJpaEntity entity = mapper.toEntity(original);
        Order restored = mapper.toDomain(entity);

        assertThat(restored.getOrderId()).isEqualTo(original.getOrderId());
        assertThat(restored.getUserId()).isEqualTo(original.getUserId());
        assertThat(restored.getStatus()).isEqualTo(original.getStatus());
        assertThat(restored.getTotalPrice()).isEqualTo(original.getTotalPrice());
        assertThat(restored.getShippingAddress().getRecipient())
                .isEqualTo(original.getShippingAddress().getRecipient());
        assertThat(restored.getShippingAddress().getAddress2())
                .isEqualTo(original.getShippingAddress().getAddress2());
        assertThat(restored.getCreatedAt()).isEqualTo(original.getCreatedAt());
        assertThat(restored.getUpdatedAt()).isEqualTo(original.getUpdatedAt());
        assertThat(restored.getItems()).hasSize(2);
        assertThat(restored.getItems().get(0).getProductId())
                .isEqualTo(original.getItems().get(0).getProductId());
        assertThat(restored.getItems().get(1).getProductId())
                .isEqualTo(original.getItems().get(1).getProductId());
    }
}
