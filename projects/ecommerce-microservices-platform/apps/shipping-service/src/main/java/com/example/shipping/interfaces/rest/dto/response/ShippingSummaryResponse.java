package com.example.shipping.interfaces.rest.dto.response;

import com.example.shipping.application.result.ShippingPeriodCountResult;

public record ShippingSummaryResponse(
        long today,
        long week,
        long month,
        long total
) {
    public static ShippingSummaryResponse from(ShippingPeriodCountResult result) {
        return new ShippingSummaryResponse(result.today(), result.week(), result.month(), result.total());
    }
}
