package com.example.payment.adapter.out.metrics;

import com.example.observability.metrics.EventMetricNames;
import com.example.payment.application.port.out.PaymentMetricRecorder;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

@Component
public class PaymentMetrics implements PaymentMetricRecorder {

    private final Counter paymentCreatedTotal;
    private final Counter paymentCompletedTotal;
    private final Counter paymentRefundedTotal;
    private final Counter paymentRefundStrandedTotal;
    private final Counter paymentRefundStrandedResolvedTotal;
    private final Counter paymentRefundStrandedUnresolvedTotal;
    private final Counter paymentAmountSum;
    private final MeterRegistry registry;

    /**
     * Backs the {@code payment_refund_stranded_open} gauge. The gauge is registered once (the
     * first {@link #registerStrandedOpenGauge} call swaps in the real supplier); Micrometer reads
     * through this reference on every scrape, so a late registration takes effect without
     * re-registering the meter.
     */
    private final AtomicReference<Supplier<Number>> strandedOpenSupplier = new AtomicReference<>(() -> 0);

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

        this.paymentRefundStrandedTotal = Counter.builder("payment_refund_stranded_total")
                .description("Total confirm() post-capture auto-refunds that failed at the PG, "
                        + "leaving captured funds stranded pending operator action (TASK-BE-437)")
                .register(registry);

        this.paymentRefundStrandedResolvedTotal = Counter.builder("payment_refund_stranded_resolved_total")
                .description("Total stranded refunds auto-healed by the reconciliation sweeper — "
                        + "PG already cancelled, or a retry cancel succeeded (TASK-BE-438)")
                .register(registry);

        this.paymentRefundStrandedUnresolvedTotal = Counter.builder("payment_refund_stranded_unresolved_total")
                .description("Total stranded refunds that reached the terminal UNRESOLVED state — "
                        + "attempt cap exhausted or a definitive PG rejection; operator must act (TASK-BE-438)")
                .register(registry);

        // Current count of open (STRANDED) obligations, for an alerting SLO (TASK-BE-438). Reads
        // through strandedOpenSupplier so a late registerStrandedOpenGauge swap takes effect.
        Gauge.builder("payment_refund_stranded_open", strandedOpenSupplier,
                        ref -> ref.get().get().doubleValue())
                .description("Current number of open (STRANDED) stranded-refund obligations awaiting reconciliation")
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
    public void incrementRefundStranded() {
        paymentRefundStrandedTotal.increment();
    }

    @Override
    public void incrementRefundStrandedResolved() {
        paymentRefundStrandedResolvedTotal.increment();
    }

    @Override
    public void incrementRefundStrandedUnresolved() {
        paymentRefundStrandedUnresolvedTotal.increment();
    }

    @Override
    public void registerStrandedOpenGauge(Supplier<Number> openCount) {
        Objects.requireNonNull(openCount, "openCount supplier must not be null");
        strandedOpenSupplier.set(openCount);
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
