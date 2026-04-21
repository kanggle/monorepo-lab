package com.example.payment.adapter.out.metrics;

import com.example.observability.metrics.EventMetricNames;
import com.example.payment.application.port.out.PaymentMetricRecorder;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class PaymentMetrics implements PaymentMetricRecorder {

    private final Counter paymentCreatedTotal;
    private final Counter paymentCompletedTotal;
    private final Counter paymentRefundedTotal;
    private final Counter paymentAmountSum;
    private final MeterRegistry registry;

    public PaymentMetrics(MeterRegistry registry) {
        Objects.requireNonNull(registry, "MeterRegistry must not be null");
        this.registry = registry;

        this.paymentCreatedTotal = Counter.builder("payment_created_total")
                .description("Total payments created")
                .register(registry);

        this.paymentCompletedTotal = Counter.builder("payment_completed_total")
                .description("Total payments successfully completed")
                .register(registry);

        this.paymentRefundedTotal = Counter.builder("payment_refunded_total")
                .description("Total refunds processed")
                .register(registry);

        this.paymentAmountSum = Counter.builder("payment_amount_sum")
                .description("Cumulative payment amount processed")
                .register(registry);
    }

    @Override
    public void incrementPaymentCreated() {
        paymentCreatedTotal.increment();
    }

    @Override
    public void incrementPaymentCompleted() {
        paymentCompletedTotal.increment();
    }

    @Override
    public void incrementPaymentRefunded() {
        paymentRefundedTotal.increment();
    }

    @Override
    public void addPaymentAmount(long amount) {
        paymentAmountSum.increment(amount);
    }

    @Override
    public void incrementEventPublishFailure(String eventType) {
        registry.counter(EventMetricNames.EVENT_PUBLISH_FAILURE_TOTAL,
                EventMetricNames.TAG_SERVICE, "payment-service",
                EventMetricNames.TAG_EVENT_TYPE, eventType)
                .increment();
    }

}
