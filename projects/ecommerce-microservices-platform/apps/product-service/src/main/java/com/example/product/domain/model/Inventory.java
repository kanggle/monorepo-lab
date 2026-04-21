package com.example.product.domain.model;

import lombok.Getter;

import java.util.UUID;

@Getter
public class Inventory {

    private final UUID variantId;
    private StockQuantity stock;

    private Inventory(UUID variantId, StockQuantity stock) {
        if (variantId == null) {
            throw new IllegalArgumentException("variantId must not be null");
        }
        if (stock == null) {
            throw new IllegalArgumentException("stock must not be null");
        }
        this.variantId = variantId;
        this.stock = stock;
    }

    public static Inventory create(UUID variantId, StockQuantity stock) {
        return new Inventory(variantId, stock);
    }

    public StockQuantity currentStock() {
        return stock;
    }

    public void increase(int amount) {
        this.stock = stock.add(new StockQuantity(amount));
    }

    public void decrease(int amount) {
        this.stock = stock.subtract(new StockQuantity(amount));
    }

    public void adjustStock(int delta) {
        if (delta > 0) {
            increase(delta);
        } else {
            decrease(-delta);
        }
    }
}
