package com.example.product.application.port;

import com.example.product.application.dto.StockAdjustmentType;

public interface ProductMetricPort {

    void incrementProductCreated();

    void incrementProductUpdated();

    void incrementProductDeleted();

    void incrementStockAdjusted(StockAdjustmentType type);

    void incrementOutOfStock();
}
