package com.example.order.presentation.dto;

public record AdminOrderStatusChangeResponse(
        String orderId,
        String status
) {}
