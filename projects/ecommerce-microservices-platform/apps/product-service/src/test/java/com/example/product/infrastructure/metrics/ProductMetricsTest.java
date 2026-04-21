package com.example.product.infrastructure.metrics;

import com.example.product.application.dto.StockAdjustmentType;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductMetricsTest {

    private MeterRegistry registry;
    private ProductMetrics productMetrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        productMetrics = new ProductMetrics(registry);
    }

    @Test
    @DisplayName("상품 생성 시 product_created_total이 증가한다")
    void incrementProductCreated_incrementsCounter() {
        productMetrics.incrementProductCreated();

        assertThat(registry.counter("product_created_total").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("상품 수정 시 product_updated_total이 증가한다")
    void incrementProductUpdated_incrementsCounter() {
        productMetrics.incrementProductUpdated();

        assertThat(registry.counter("product_updated_total").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("상품 삭제 시 product_deleted_total이 증가한다")
    void incrementProductDeleted_incrementsCounter() {
        productMetrics.incrementProductDeleted();

        assertThat(registry.counter("product_deleted_total").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("재고 조정 시 type별 product_stock_adjusted_total이 증가한다")
    void incrementStockAdjusted_incrementsCounterByType() {
        productMetrics.incrementStockAdjusted(StockAdjustmentType.INCREASE);
        productMetrics.incrementStockAdjusted(StockAdjustmentType.DECREASE);
        productMetrics.incrementStockAdjusted(StockAdjustmentType.RESERVE);
        productMetrics.incrementStockAdjusted(StockAdjustmentType.INCREASE);

        assertThat(registry.counter("product_stock_adjusted_total", "type", "increase").count()).isEqualTo(2.0);
        assertThat(registry.counter("product_stock_adjusted_total", "type", "decrease").count()).isEqualTo(1.0);
        assertThat(registry.counter("product_stock_adjusted_total", "type", "reserve").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("품절 시 product_out_of_stock_total이 증가한다")
    void incrementOutOfStock_incrementsCounter() {
        productMetrics.incrementOutOfStock();

        assertThat(registry.counter("product_out_of_stock_total").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("incrementStockAdjusted 1000회 반복 호출 시 동일 태그 조합에 대해 Counter가 중복 등록되지 않는다")
    void incrementStockAdjusted_repeated_noCounterLeak() {
        for (int i = 0; i < 1000; i++) {
            productMetrics.incrementStockAdjusted(StockAdjustmentType.INCREASE);
        }

        long meterCount = registry.getMeters().stream()
                .filter(m -> m.getId().getName().equals("product_stock_adjusted_total")
                        && "increase".equals(m.getId().getTag("type")))
                .count();
        assertThat(meterCount).isEqualTo(1);
        assertThat(registry.counter("product_stock_adjusted_total", "type", "increase").count())
                .isEqualTo(1000.0);
    }
}
