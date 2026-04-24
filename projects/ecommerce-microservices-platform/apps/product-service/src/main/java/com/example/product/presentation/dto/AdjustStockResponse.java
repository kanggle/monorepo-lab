package com.example.product.presentation.dto;

import com.example.product.application.dto.AdjustStockResult;

public record AdjustStockResponse(String variantId, int currentStock) {

    public static AdjustStockResponse from(AdjustStockResult result) {
        return new AdjustStockResponse(result.variantId().toString(), result.currentStock());
    }
}
