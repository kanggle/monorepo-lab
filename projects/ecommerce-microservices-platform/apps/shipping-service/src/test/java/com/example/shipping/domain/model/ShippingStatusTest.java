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

    // ─── TASK-BE-649 (ADR-MONO-022 D4 v2(d)) — terminal CANCELLED ──────────

    @Test
    @DisplayName("BE-649: PREPARING -> CANCELLED 허용 (PREPARING -> SHIPPED 도 그대로 허용)")
    void preparingToCancelled_allowed_andShippedStillAllowed() {
        assertThat(ShippingStatus.PREPARING.canTransitionTo(ShippingStatus.CANCELLED)).isTrue();
        assertThat(ShippingStatus.PREPARING.canTransitionTo(ShippingStatus.SHIPPED)).isTrue();
    }

    @Test
    @DisplayName("BE-649: 출고 뒤(SHIPPED / IN_TRANSIT / DELIVERED)에서는 CANCELLED 불가")
    void afterDispatch_toCancelled_notAllowed() {
        assertThat(ShippingStatus.SHIPPED.canTransitionTo(ShippingStatus.CANCELLED)).isFalse();
        assertThat(ShippingStatus.IN_TRANSIT.canTransitionTo(ShippingStatus.CANCELLED)).isFalse();
        assertThat(ShippingStatus.DELIVERED.canTransitionTo(ShippingStatus.CANCELLED)).isFalse();
    }

    @Test
    @DisplayName("BE-649: CANCELLED 는 끝 상태 — 어떤 전이도 불가 (자기 전이 포함)")
    void cancelled_isTerminal() {
        for (ShippingStatus target : ShippingStatus.values()) {
            assertThat(ShippingStatus.CANCELLED.canTransitionTo(target))
                    .as("CANCELLED -> %s", target)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("BE-649: CANCELLED 는 정방향 사슬의 맨 뒤 — ordinal 기반 전진기가 넘어가지 못한다")
    void cancelled_isLastOrdinal() {
        // ShippingForwardAdvancer walks values() by ordinal toward a goal; CANCELLED must sit
        // after DELIVERED so no carrier goal is ever "ahead" of a cancelled shipment, and so
        // no forward step from DELIVERED could land on it.
        ShippingStatus[] values = ShippingStatus.values();
        assertThat(values[values.length - 1]).isEqualTo(ShippingStatus.CANCELLED);
        assertThat(ShippingStatus.CANCELLED.ordinal()).isGreaterThan(ShippingStatus.DELIVERED.ordinal());
    }
}
