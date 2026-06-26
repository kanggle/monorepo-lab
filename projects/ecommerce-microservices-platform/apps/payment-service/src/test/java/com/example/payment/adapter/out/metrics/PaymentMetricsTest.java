package com.example.payment.adapter.out.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentMetricsTest {

    private MeterRegistry registry;
    private PaymentMetrics paymentMetrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        paymentMetrics = new PaymentMetrics(registry);
    }

    @Test
    @DisplayName("결제 생성 시 payment_created_total이 증가한다")
    void incrementPaymentCreated_incrementsCounter() {
        paymentMetrics.incrementPaymentCreated();

        assertThat(registry.counter("payment_created_total").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("결제 완료 시 payment_completed_total이 증가한다")
    void incrementPaymentCompleted_incrementsCounter() {
        paymentMetrics.incrementPaymentCompleted();

        assertThat(registry.counter("payment_completed_total").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("환불 처리 시 payment_refunded_total이 증가한다")
    void incrementPaymentRefunded_incrementsCounter() {
        paymentMetrics.incrementPaymentRefunded();

        assertThat(registry.counter("payment_refunded_total").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("결제 금액이 payment_amount_sum에 누적된다")
    void addPaymentAmount_incrementsCounter() {
        paymentMetrics.addPaymentAmount(50000);
        paymentMetrics.addPaymentAmount(30000);

        assertThat(registry.counter("payment_amount_sum").count()).isEqualTo(80000.0);
    }

    @Test
    @DisplayName("TASK-BE-438: stranded refund 자동복구 시 payment_refund_stranded_resolved_total 이 증가한다")
    void incrementRefundStrandedResolved_incrementsCounter() {
        paymentMetrics.incrementRefundStrandedResolved();
        paymentMetrics.incrementRefundStrandedResolved();

        assertThat(registry.counter("payment_refund_stranded_resolved_total").count()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("TASK-BE-438: stranded refund terminal 시 payment_refund_stranded_unresolved_total 이 증가한다")
    void incrementRefundStrandedUnresolved_incrementsCounter() {
        paymentMetrics.incrementRefundStrandedUnresolved();

        assertThat(registry.counter("payment_refund_stranded_unresolved_total").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("TASK-BE-438: payment_refund_stranded_open 게이지는 등록된 supplier 의 현재 open 개수를 노출한다")
    void registerStrandedOpenGauge_exposesCurrentOpenCount() {
        long[] open = {3L};
        paymentMetrics.registerStrandedOpenGauge(() -> open[0]);
        assertThat(registry.get("payment_refund_stranded_open").gauge().value()).isEqualTo(3.0);

        open[0] = 5L; // gauge reads through the supplier on each scrape
        assertThat(registry.get("payment_refund_stranded_open").gauge().value()).isEqualTo(5.0);
    }

    @Test
    @DisplayName("TASK-BE-438: supplier 등록 전 open 게이지는 0 을 노출한다 (기본 supplier)")
    void strandedOpenGauge_defaultsToZeroBeforeRegistration() {
        assertThat(registry.get("payment_refund_stranded_open").gauge().value()).isEqualTo(0.0);
    }

}
