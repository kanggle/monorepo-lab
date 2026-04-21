package com.example.product.infrastructure.metrics;

import com.example.observability.metrics.EventMetricNames;
import com.example.product.application.dto.StockAdjustmentType;
import com.example.product.application.port.ProductMetricPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class ProductMetrics implements ProductMetricPort {

    private final MeterRegistry registry;
    private final Counter productCreatedTotal;
    private final Counter productUpdatedTotal;
    private final Counter productDeletedTotal;
    private final Counter stockAdjustedIncrease;
    private final Counter stockAdjustedDecrease;
    private final Counter stockAdjustedReserve;
    private final Counter outOfStockTotal;

    public ProductMetrics(MeterRegistry registry) {
        Objects.requireNonNull(registry, "MeterRegistry must not be null");
        this.registry = registry;
        this.productCreatedTotal = Counter.builder("product_created_total")
                .description("Total products created")
                .register(registry);

        this.productUpdatedTotal = Counter.builder("product_updated_total")
                .description("Total product updates")
                .register(registry);

        this.productDeletedTotal = Counter.builder("product_deleted_total")
                .description("Total products deleted")
                .register(registry);

        this.stockAdjustedIncrease = Counter.builder("product_stock_adjusted_total")
                .description("Total stock adjustments by type")
                .tag("type", "increase")
                .register(registry);

        this.stockAdjustedDecrease = Counter.builder("product_stock_adjusted_total")
                .description("Total stock adjustments by type")
                .tag("type", "decrease")
                .register(registry);

        this.stockAdjustedReserve = Counter.builder("product_stock_adjusted_total")
                .description("Total stock adjustments by type")
                .tag("type", "reserve")
                .register(registry);

        this.outOfStockTotal = Counter.builder("product_out_of_stock_total")
                .description("Total out-of-stock events")
                .register(registry);
    }

    public void incrementProductCreated() {
        productCreatedTotal.increment();
    }

    public void incrementProductUpdated() {
        productUpdatedTotal.increment();
    }

    public void incrementProductDeleted() {
        productDeletedTotal.increment();
    }

    public void incrementStockAdjusted(StockAdjustmentType type) {
        switch (type) {
            case INCREASE -> stockAdjustedIncrease.increment();
            case RESERVE -> stockAdjustedReserve.increment();
            case DECREASE -> stockAdjustedDecrease.increment();
        }
    }

    public void incrementOutOfStock() {
        outOfStockTotal.increment();
    }

    public void incrementEventPublishFailure(String eventType) {
        registry.counter(EventMetricNames.EVENT_PUBLISH_FAILURE_TOTAL,
                EventMetricNames.TAG_SERVICE, "product-service",
                EventMetricNames.TAG_EVENT_TYPE, eventType)
                .increment();
    }
}
