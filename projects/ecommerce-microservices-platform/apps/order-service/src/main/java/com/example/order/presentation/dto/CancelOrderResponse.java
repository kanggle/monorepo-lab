package com.example.order.presentation.dto;

import com.example.order.application.dto.CancelOrderResult;

public record CancelOrderResponse(String orderId, String status) {

    public static CancelOrderResponse from(CancelOrderResult result) {
        return new CancelOrderResponse(result.orderId(), result.status());
    }
}
