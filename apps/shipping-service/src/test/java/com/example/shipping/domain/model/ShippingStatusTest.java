package com.example.shipping.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ShippingStatus 전이 규칙 테스트")
class ShippingStatusTest {

    @Test
    @DisplayName("허용된 전이: PREPARING -> SHIPPED")
    void preparingToShipped_allowed() {
        assertThat(ShippingStatus.PREPARING.canTransitionTo(ShippingStatus.SHIPPED)).isTrue();
    }

    @Test
    @DisplayName("허용된 전이: SHIPPED -> IN_TRANSIT")
    void shippedToInTransit_allowed() {
        assertThat(ShippingStatus.SHIPPED.canTransitionTo(ShippingStatus.IN_TRANSIT)).isTrue();
    }

    @Test
    @DisplayName("허용된 전이: IN_TRANSIT -> DELIVERED")
    void inTransitToDelivered_allowed() {
        assertThat(ShippingStatus.IN_TRANSIT.canTransitionTo(ShippingStatus.DELIVERED)).isTrue();
    }

    @Test
    @DisplayName("역방향 전이 불가: SHIPPED -> PREPARING")
    void shippedToPreparing_notAllowed() {
        assertThat(ShippingStatus.SHIPPED.canTransitionTo(ShippingStatus.PREPARING)).isFalse();
    }

    @Test
    @DisplayName("건너뛰기 전이 불가: PREPARING -> IN_TRANSIT")
    void preparingToInTransit_notAllowed() {
        assertThat(ShippingStatus.PREPARING.canTransitionTo(ShippingStatus.IN_TRANSIT)).isFalse();
    }

    @Test
    @DisplayName("DELIVERED에서 전이 불가")
    void delivered_noTransitions() {
        for (ShippingStatus target : ShippingStatus.values()) {
            assertThat(ShippingStatus.DELIVERED.canTransitionTo(target)).isFalse();
        }
    }
}
