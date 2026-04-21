package com.example.product.application.dto;

public enum StockAdjustmentType {
    INCREASE,
    DECREASE,
    RESERVE;

    public static StockAdjustmentType of(int quantity, String reason) {
        if (reason != null && reason.contains("reserve")) {
            return RESERVE;
        }
        return quantity > 0 ? INCREASE : DECREASE;
    }
}
