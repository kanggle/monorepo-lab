package com.example.order.infrastructure.metrics;

import com.example.observability.metrics.EventMetricNames;
import com.example.order.application.port.OrderMetricsPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class MicrometerOrderMetrics implements OrderMetricsPort {

    private final Counter orderPlacedTotal;
    private final Counter orderConfirmedTotal;
    private final Counter orderCancelledByUser;
    private final Counter orderCancelledByStockInsufficient;
    private final Counter orderCancelledByUserWithdrawn;
    private final Counter orderCancelledByTimeout;
    private final Counter orderAmountSum;
    private final MeterRegistry registry;

    public MicrometerOrderMetrics(MeterRegistry registry) {
        Objects.requireNonNull(registry, "MeterRegistry must not be null");
        this.registry = registry;

        this.orderPlacedTotal = Counter.builder("order_placed_total")
                .description("Total orders placed")
                .register(registry);

        this.orderConfirmedTotal = Counter.builder("order_confirmed_total")
                .description("Total orders confirmed (stock reserved)")
                .register(registry);

        this.orderCancelledByUser = cancelledCounter(registry, "user");
        this.orderCancelledByUserWithdrawn = cancelledCounter(registry, "user_withdrawn");
        this.orderCancelledByStockInsufficient = cancelledCounter(registry, "stock_insufficient");
        this.orderCancelledByTimeout = cancelledCounter(registry, "timeout");

        this.orderAmountSum = Counter.builder("order_amount_sum")
                .description("Cumulative order amount")
                .register(registry);
    }

    private static Counter cancelledCounter(MeterRegistry registry, String reason) {
        return Counter.builder("order_cancelled_total")
                .description("Total orders cancelled by reason")
                .tag("reason", reason)
                .register(registry);
    }

    @Override
    public void recordOrderPlaced() {
        orderPlacedTotal.increment();
    }

    @Override
    public void recordOrderConfirmed() {
        orderConfirmedTotal.increment();
    }

    @Override
    public void recordOrderCancelled(String reason) {
        switch (reason) {
            case "user_withdrawn" -> orderCancelledByUserWithdrawn.increment();
            case "stock_insufficient" -> orderCancelledByStockInsufficient.increment();
            case "timeout" -> orderCancelledByTimeout.increment();
            default -> orderCancelledByUser.increment();
        }
    }

    @Override
    public void recordStatusTransition(String from, String to) {
        registry.counter("order_status_transition_total", "from", from, "to", to)
                .increment();
    }

    @Override
    public void recordOrderAmount(long amount) {
        orderAmountSum.increment(amount);
    }

    @Override
    public void recordEventPublishFailure(String eventType) {
        registry.counter(EventMetricNames.EVENT_PUBLISH_FAILURE_TOTAL,
                EventMetricNames.TAG_SERVICE, "order-service",
                EventMetricNames.TAG_EVENT_TYPE, eventType)
                .increment();
    }
}
