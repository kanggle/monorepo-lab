package com.example.shipping.domain.model;

import com.example.shipping.domain.exception.InvalidShippingException;
import com.example.shipping.domain.exception.InvalidStatusTransitionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Shipping 도메인 모델 단위 테스트")
class ShippingTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    @Test
    @DisplayName("배송을 생성하면 PREPARING 상태이고 상태 히스토리에 기록된다")
    void create_validInput_preparingStatus() {
        Shipping shipping = Shipping.create("tenant-a", "order-1", "user-1", clock);

        assertThat(shipping.getShippingId()).isNotBlank();
        assertThat(shipping.getOrderId()).isEqualTo("order-1");
        assertThat(shipping.getUserId()).isEqualTo("user-1");
        assertThat(shipping.getStatus()).isEqualTo(ShippingStatus.PREPARING);
        assertThat(shipping.getStatusHistory()).hasSize(1);
        assertThat(shipping.getStatusHistory().get(0).status()).isEqualTo(ShippingStatus.PREPARING);
        assertThat(shipping.getCreatedAt()).isNotNull();
        assertThat(shipping.getUpdatedAt()).isNotNull();
        assertThat(shipping.getTenantId()).isEqualTo("tenant-a");
    }

    @Test
    @DisplayName("tenantId가 blank/null이면 기본 테넌트(ecommerce)로 stamp된다 (net-zero, D8)")
    void create_blankTenant_defaultsToEcommerce() {
        Shipping shipping = Shipping.create(" ", "order-1", "user-1", clock);

        assertThat(shipping.getTenantId()).isEqualTo("ecommerce");
    }

    @Test
    @DisplayName("orderId가 null이면 생성 실패")
    void create_nullOrderId_throws() {
        assertThatThrownBy(() -> Shipping.create("tenant-a", null, "user-1", clock))
                .isInstanceOf(InvalidShippingException.class);
    }

    @Test
    @DisplayName("userId가 blank이면 생성 실패")
    void create_blankUserId_throws() {
        assertThatThrownBy(() -> Shipping.create("tenant-a", "order-1", " ", clock))
                .isInstanceOf(InvalidShippingException.class);
    }

    @Test
    @DisplayName("PREPARING -> SHIPPED 전이 성공 (trackingNumber, carrier 필수)")
    void transition_preparingToShipped_success() {
        Shipping shipping = Shipping.create("tenant-a", "order-1", "user-1", clock);

        ShippingStatus previous = shipping.transitionTo(ShippingStatus.SHIPPED, "TRK-001", "CJ대한통운", clock);

        assertThat(previous).isEqualTo(ShippingStatus.PREPARING);
        assertThat(shipping.getStatus()).isEqualTo(ShippingStatus.SHIPPED);
        assertThat(shipping.getTrackingNumber()).isEqualTo("TRK-001");
        assertThat(shipping.getCarrier()).isEqualTo("CJ대한통운");
        assertThat(shipping.getStatusHistory()).hasSize(2);
    }

    @Test
    @DisplayName("SHIPPED 전이 시 trackingNumber가 없으면 실패")
    void transition_toShipped_noTrackingNumber_throws() {
        Shipping shipping = Shipping.create("tenant-a", "order-1", "user-1", clock);

        assertThatThrownBy(() -> shipping.transitionTo(ShippingStatus.SHIPPED, null, "CJ대한통운", clock))
                .isInstanceOf(InvalidShippingException.class)
                .hasMessageContaining("Tracking number");
    }

    @Test
    @DisplayName("SHIPPED 전이 시 carrier가 없으면 실패")
    void transition_toShipped_noCarrier_throws() {
        Shipping shipping = Shipping.create("tenant-a", "order-1", "user-1", clock);

        assertThatThrownBy(() -> shipping.transitionTo(ShippingStatus.SHIPPED, "TRK-001", "", clock))
                .isInstanceOf(InvalidShippingException.class)
                .hasMessageContaining("Carrier");
    }

    @Test
    @DisplayName("SHIPPED -> IN_TRANSIT 전이 성공")
    void transition_shippedToInTransit_success() {
        Shipping shipping = Shipping.create("tenant-a", "order-1", "user-1", clock);
        shipping.transitionTo(ShippingStatus.SHIPPED, "TRK-001", "CJ대한통운", clock);

        ShippingStatus previous = shipping.transitionTo(ShippingStatus.IN_TRANSIT, null, null, clock);

        assertThat(previous).isEqualTo(ShippingStatus.SHIPPED);
        assertThat(shipping.getStatus()).isEqualTo(ShippingStatus.IN_TRANSIT);
        assertThat(shipping.getStatusHistory()).hasSize(3);
    }

    @Test
    @DisplayName("IN_TRANSIT -> DELIVERED 전이 성공")
    void transition_inTransitToDelivered_success() {
        Shipping shipping = Shipping.create("tenant-a", "order-1", "user-1", clock);
        shipping.transitionTo(ShippingStatus.SHIPPED, "TRK-001", "CJ대한통운", clock);
        shipping.transitionTo(ShippingStatus.IN_TRANSIT, null, null, clock);

        ShippingStatus previous = shipping.transitionTo(ShippingStatus.DELIVERED, null, null, clock);

        assertThat(previous).isEqualTo(ShippingStatus.IN_TRANSIT);
        assertThat(shipping.getStatus()).isEqualTo(ShippingStatus.DELIVERED);
        assertThat(shipping.getStatusHistory()).hasSize(4);
    }

    @Test
    @DisplayName("역방향 전이 불가: SHIPPED -> PREPARING")
    void transition_shippedToPreparing_throws() {
        Shipping shipping = Shipping.create("tenant-a", "order-1", "user-1", clock);
        shipping.transitionTo(ShippingStatus.SHIPPED, "TRK-001", "CJ대한통운", clock);

        assertThatThrownBy(() -> shipping.transitionTo(ShippingStatus.PREPARING, null, null, clock))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    @DisplayName("건너뛰기 전이 불가: PREPARING -> IN_TRANSIT")
    void transition_preparingToInTransit_throws() {
        Shipping shipping = Shipping.create("tenant-a", "order-1", "user-1", clock);

        assertThatThrownBy(() -> shipping.transitionTo(ShippingStatus.IN_TRANSIT, null, null, clock))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    @DisplayName("건너뛰기 전이 불가: PREPARING -> DELIVERED")
    void transition_preparingToDelivered_throws() {
        Shipping shipping = Shipping.create("tenant-a", "order-1", "user-1", clock);

        assertThatThrownBy(() -> shipping.transitionTo(ShippingStatus.DELIVERED, null, null, clock))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    @DisplayName("DELIVERED 상태에서 어떤 전이도 불가")
    void transition_delivered_noTransition() {
        Shipping shipping = Shipping.create("tenant-a", "order-1", "user-1", clock);
        shipping.transitionTo(ShippingStatus.SHIPPED, "TRK-001", "CJ대한통운", clock);
        shipping.transitionTo(ShippingStatus.IN_TRANSIT, null, null, clock);
        shipping.transitionTo(ShippingStatus.DELIVERED, null, null, clock);

        assertThatThrownBy(() -> shipping.transitionTo(ShippingStatus.PREPARING, null, null, clock))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    @DisplayName("statusHistory는 불변 리스트")
    void getStatusHistory_returnsUnmodifiableList() {
        Shipping shipping = Shipping.create("tenant-a", "order-1", "user-1", clock);

        assertThatThrownBy(() -> shipping.getStatusHistory().add(
                new StatusHistoryEntry(ShippingStatus.SHIPPED, Instant.now())))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
