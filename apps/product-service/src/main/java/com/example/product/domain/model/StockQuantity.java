package com.example.product.domain.model;

import com.example.product.domain.exception.InsufficientStockException;

public record StockQuantity(int value) {

    public StockQuantity {
        if (value < 0) {
            throw new IllegalArgumentException("Stock cannot be negative");
        }
    }

    public StockQuantity add(StockQuantity other) {
        return new StockQuantity(Math.addExact(this.value, other.value));
    }

    public StockQuantity subtract(StockQuantity other) {
        if (this.value < other.value) {
            throw new InsufficientStockException(
                    "Cannot subtract " + other.value + " from stock " + this.value + ": result would be negative");
        }
        return new StockQuantity(this.value - other.value);
    }
}
