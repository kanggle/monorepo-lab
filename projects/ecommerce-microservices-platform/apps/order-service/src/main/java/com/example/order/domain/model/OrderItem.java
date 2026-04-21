package com.example.order.domain.model;

import lombok.Getter;

@Getter
public class OrderItem {

    private String id;
    private String productId;
    private String variantId;
    private String productName;
    private String optionName;
    private int quantity;
    private long unitPrice;

    private OrderItem() {
    }

    OrderItem(String id, String productId, String variantId,
              String productName, String optionName, int quantity, long unitPrice) {
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be greater than 0");
        if (unitPrice <= 0) throw new IllegalArgumentException("unitPrice must be greater than 0");
        this.id = id;
        this.productId = productId;
        this.variantId = variantId;
        this.productName = productName;
        this.optionName = optionName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    public static OrderItem reconstitute(String id, String productId, String variantId,
                                          String productName, String optionName,
                                          int quantity, long unitPrice) {
        OrderItem item = new OrderItem();
        item.id = id;
        item.productId = productId;
        item.variantId = variantId;
        item.productName = productName;
        item.optionName = optionName;
        item.quantity = quantity;
        item.unitPrice = unitPrice;
        return item;
    }

    public long subtotal() {
        return unitPrice * quantity;
    }
}
