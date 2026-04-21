package com.example.order.presentation.dto;

import com.example.order.application.dto.PlaceOrderResult;

public record PlaceOrderResponse(String orderId) {

    public static PlaceOrderResponse from(PlaceOrderResult result) {
        return new PlaceOrderResponse(result.orderId());
    }
}
