package com.example.order.application.dto;

/**
 * @param totalPrice     the amount to charge — net of {@code discountAmount} (TASK-INT-026)
 * @param discountAmount the coupon discount; 0 without a coupon
 */
public record PlaceOrderResult(String orderId, long totalPrice, long discountAmount) {}
