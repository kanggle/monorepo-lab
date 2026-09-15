package com.example.order.presentation.dto;

import com.example.order.application.dto.PlaceOrderResult;

/**
 * {@code totalPrice} is the amount the client must request from the PG — net of
 * {@code discountAmount} (TASK-INT-026). payment-service confirms against the same value.
 */
public record PlaceOrderResponse(String orderId, long totalPrice, long discountAmount) {

    public static PlaceOrderResponse from(PlaceOrderResult result) {
        return new PlaceOrderResponse(result.orderId(), result.totalPrice(), result.discountAmount());
    }
}
