package com.example.order.domain.model;

import lombok.Getter;

@Getter
public class OrderItem {

    /** Per-tenant default seller — the single-seller degradation fallback (D8, AC-5). */
    public static final String DEFAULT_SELLER_ID = "default";

    private String id;
    private String productId;
    private String variantId;
    private String productName;
    private String optionName;
    private int quantity;
    private long unitPrice;
    /**
     * Owning seller of this line (ADR-MONO-030 Step 3 §3.2 — order-line attribution).
     * A denormalized snapshot supplied at placement (the client carries it exactly
     * like {@code productName}/{@code unitPrice}; order-service does NOT call
     * product-service). Captured immutably on the line. A single order may span
     * multiple sellers (each line attributed independently); the order header stays
     * tenant-only. Absent at placement → {@link #DEFAULT_SELLER_ID} (degrade).
     */
    private String sellerId;

    private OrderItem() {
    }

    /** Backward-compatible (no seller) — line is attributed to the default seller (D8). */
    OrderItem(String id, String productId, String variantId,
              String productName, String optionName, int quantity, long unitPrice) {
        this(id, productId, variantId, productName, optionName, quantity, unitPrice, DEFAULT_SELLER_ID);
    }

    OrderItem(String id, String productId, String variantId,
              String productName, String optionName, int quantity, long unitPrice, String sellerId) {
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be greater than 0");
        if (unitPrice <= 0) throw new IllegalArgumentException("unitPrice must be greater than 0");
        this.id = id;
        this.productId = productId;
        this.variantId = variantId;
        this.productName = productName;
        this.optionName = optionName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.sellerId = normalizeSeller(sellerId);
    }

    /** Backward-compatible reconstitute (no seller) — defaults to the default seller (D8). */
    public static OrderItem reconstitute(String id, String productId, String variantId,
                                          String productName, String optionName,
                                          int quantity, long unitPrice) {
        return reconstitute(id, productId, variantId, productName, optionName,
                quantity, unitPrice, DEFAULT_SELLER_ID);
    }

    public static OrderItem reconstitute(String id, String productId, String variantId,
                                          String productName, String optionName,
                                          int quantity, long unitPrice, String sellerId) {
        OrderItem item = new OrderItem();
        item.id = id;
        item.productId = productId;
        item.variantId = variantId;
        item.productName = productName;
        item.optionName = optionName;
        item.quantity = quantity;
        item.unitPrice = unitPrice;
        item.sellerId = normalizeSeller(sellerId);
        return item;
    }

    private static String normalizeSeller(String sellerId) {
        return (sellerId == null || sellerId.isBlank()) ? DEFAULT_SELLER_ID : sellerId.trim();
    }

    public long subtotal() {
        return unitPrice * quantity;
    }
}
