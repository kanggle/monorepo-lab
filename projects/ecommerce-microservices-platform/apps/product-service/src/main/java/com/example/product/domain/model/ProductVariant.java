package com.example.product.domain.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ProductVariant {

    private UUID id;
    private UUID productId;
    private String optionName;
    private StockQuantity stock;
    private Price additionalPrice;
    /**
     * Business key (== wms {@code skuCode}). Nullable: variants created before
     * TASK-MONO-198 (or via the create() path) may not carry one; only variants
     * with a SKU participate in wms inventory reconciliation (ADR-MONO-022 §D4 v2(b)).
     */
    private String sku;

    public static ProductVariant create(String optionName, StockQuantity stock, Price additionalPrice) {
        validateOptionName(optionName);
        if (stock == null) {
            throw new IllegalArgumentException("Stock must not be null");
        }
        if (additionalPrice == null) {
            throw new IllegalArgumentException("Additional price must not be null");
        }

        ProductVariant variant = new ProductVariant();
        variant.id = UUID.randomUUID();
        variant.optionName = optionName.trim();
        variant.stock = stock;
        variant.additionalPrice = additionalPrice;
        return variant;
    }

    public static ProductVariant reconstitute(UUID id, UUID productId, String optionName,
                                              StockQuantity stock, Price additionalPrice, String sku) {
        ProductVariant variant = new ProductVariant();
        variant.id = id;
        variant.productId = productId;
        variant.optionName = optionName;
        variant.stock = stock;
        variant.additionalPrice = additionalPrice;
        variant.sku = sku;
        return variant;
    }

    void assignProduct(UUID productId) {
        if (this.productId != null) {
            throw new IllegalStateException("Variant is already assigned to a product");
        }
        this.productId = productId;
    }

    public void updateOption(String optionName, Price additionalPrice) {
        validateOptionName(optionName);
        if (additionalPrice == null) {
            throw new IllegalArgumentException("Additional price must not be null");
        }
        this.optionName = optionName.trim();
        this.additionalPrice = additionalPrice;
    }

    private static void validateOptionName(String optionName) {
        if (optionName == null || optionName.isBlank()) {
            throw new IllegalArgumentException("Option name must not be blank");
        }
        if (optionName.trim().length() > 100) {
            throw new IllegalArgumentException("Option name must not exceed 100 characters");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProductVariant pv)) return false;
        return id != null && id.equals(pv.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
