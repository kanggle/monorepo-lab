package com.example.order.infrastructure.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MicrometerOrderMetrics 단위 테스트")
class MicrometerOrderMetricsTest {

    private MeterRegistry registry;
    private MicrometerOrderMetrics orderMetrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        orderMetrics = new MicrometerOrderMetrics(registry);
    }

    @Test
    @DisplayName("주문 생성 시 order_placed_total이 증가한다")
    void recordOrderPlaced_incrementsCounter() {
        orderMetrics.recordOrderPlaced();
        orderMetrics.recordOrderPlaced();

        assertThat(registry.counter("order_placed_total").count()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("주문 확인 시 order_confirmed_total이 증가한다")
    void recordOrderConfirmed_incrementsCounter() {
        orderMetrics.recordOrderConfirmed();

        assertThat(registry.counter("order_confirmed_total").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("주문 취소 시 reason별 order_cancelled_total이 증가한다")
    void recordOrderCancelled_incrementsCounterByReason() {
        orderMetrics.recordOrderCancelled("user");
        orderMetrics.recordOrderCancelled("stock_insufficient");
        orderMetrics.recordOrderCancelled("user");

        assertThat(registry.counter("order_cancelled_total", "reason", "user").count()).isEqualTo(2.0);
        assertThat(registry.counter("order_cancelled_total", "reason", "stock_insufficient").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("회원탈퇴 취소 시 order_cancelled_total{reason=user_withdrawn} 카운터가 증가한다")
    void recordOrderCancelled_userWithdrawn_incrementsUserWithdrawnCounter() {
        orderMetrics.recordOrderCancelled("user_withdrawn");
        orderMetrics.recordOrderCancelled("user_withdrawn");

        assertThat(registry.counter("order_cancelled_total", "reason", "user_withdrawn").count()).isEqualTo(2.0);
        assertThat(registry.counter("order_cancelled_total", "reason", "user").count()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("알 수 없는 reason은 기존 default(user) 카운터로 집계된다")
    void recordOrderCancelled_unknownReason_incrementsUserCounter() {
        orderMetrics.recordOrderCancelled("unknown_reason");

        assertThat(registry.counter("order_cancelled_total", "reason", "user").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("상태 전이 시 order_status_transition_total이 증가한다")
    void recordStatusTransition_incrementsCounter() {
        orderMetrics.recordStatusTransition("PENDING", "CONFIRMED");

        assertThat(registry.counter("order_status_transition_total", "from", "PENDING", "to", "CONFIRMED").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("주문 금액이 order_amount_sum에 누적된다")
    void recordOrderAmount_incrementsCounter() {
        orderMetrics.recordOrderAmount(10000);
        orderMetrics.recordOrderAmount(25000);

        assertThat(registry.counter("order_amount_sum").count()).isEqualTo(35000.0);
    }

    @Test
    @DisplayName("상태 전이를 1000번 호출해도 동일 태그 조합에 대해 Counter가 중복 등록되지 않는다")
    void recordStatusTransition_repeated_noCounterLeak() {
        for (int i = 0; i < 1000; i++) {
            orderMetrics.recordStatusTransition("PENDING", "CONFIRMED");
        }

        long meterCount = registry.getMeters().stream()
                .filter(m -> m.getId().getName().equals("order_status_transition_total"))
                .count();
        assertThat(meterCount).isEqualTo(1);
        assertThat(registry.counter("order_status_transition_total", "from", "PENDING", "to", "CONFIRMED").count()).isEqualTo(1000.0);
    }

    @Test
    @DisplayName("주문 금액을 1000번 호출해도 Counter가 중복 등록되지 않는다")
    void recordOrderAmount_repeated_noCounterLeak() {
        for (int i = 0; i < 1000; i++) {
            orderMetrics.recordOrderAmount(100);
        }

        long meterCount = registry.getMeters().stream()
                .filter(m -> m.getId().getName().equals("order_amount_sum"))
                .count();
        assertThat(meterCount).isEqualTo(1);
        assertThat(registry.counter("order_amount_sum").count()).isEqualTo(100000.0);
    }
}
