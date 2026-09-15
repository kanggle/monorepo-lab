package com.example.settlement.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PromotionCost — 프로모션 비용 행의 부호·연결 불변식 (TASK-BE-592)")
class PromotionCostTest {

    private static final Instant NOW = Instant.parse("2026-09-15T00:00:00Z");

    @Test
    @DisplayName("COST 행은 양수이고 부모 연결이 없다")
    void cost_isPositiveAndUnlinked() {
        PromotionCost c = PromotionCost.cost("tenantA", "order-1", "pay-1", "coupon-1", 5_000L, NOW);

        assertThat(c.type()).isEqualTo(PromotionCostType.COST);
        assertThat(c.amountMinor()).isEqualTo(5_000L);
        assertThat(c.reversesCostId()).isNull();
        assertThat(c.couponId()).isEqualTo("coupon-1");
        assertThat(c.costId()).isNotBlank();
    }

    @Test
    @DisplayName("0 또는 음수 COST 는 거부한다")
    void cost_nonPositive_isRejected() {
        assertThatThrownBy(() -> PromotionCost.cost("tenantA", "order-1", "pay-1", null, 0L, NOW))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> PromotionCost.cost("tenantA", "order-1", "pay-1", null, -1L, NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("REVERSAL 은 음수이고 부모 COST 에 연결되며 원래 행은 바뀌지 않는다")
    void toReversal_isNegativeAndLinked() {
        PromotionCost c = PromotionCost.cost("tenantA", "order-1", "pay-1", "coupon-1", 5_000L, NOW);

        PromotionCost r = c.toReversal("refund-1", NOW, 2_000L);

        assertThat(r.type()).isEqualTo(PromotionCostType.REVERSAL);
        assertThat(r.amountMinor()).isEqualTo(-2_000L);
        assertThat(r.reversesCostId()).isEqualTo(c.costId());
        assertThat(r.paymentId()).isEqualTo("refund-1");
        assertThat(r.tenantId()).isEqualTo("tenantA");
        assertThat(r.orderId()).isEqualTo("order-1");
        assertThat(c.amountMinor()).isEqualTo(5_000L);
    }

    @Test
    @DisplayName("REVERSAL 을 다시 되돌릴 수 없고, 양수 REVERSAL·연결 없는 REVERSAL 은 만들 수 없다")
    void invalidReversals_areRejected() {
        PromotionCost c = PromotionCost.cost("tenantA", "order-1", "pay-1", null, 5_000L, NOW);
        PromotionCost r = c.toReversal("refund-1", NOW, 1_000L);

        assertThatThrownBy(() -> r.toReversal("refund-2", NOW, 500L)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new PromotionCost("x", "tenantA", "order-1", "refund-1", null,
                PromotionCostType.REVERSAL, 1_000L, c.costId(), NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new PromotionCost("x", "tenantA", "order-1", "refund-1", null,
                PromotionCostType.REVERSAL, -1_000L, null, NOW)).isInstanceOf(IllegalStateException.class);
    }
}
