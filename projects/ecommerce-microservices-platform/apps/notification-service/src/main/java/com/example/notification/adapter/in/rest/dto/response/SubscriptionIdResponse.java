package com.example.notification.adapter.in.rest.dto.response;

import com.example.notification.application.result.RegisterSubscriptionResult;

public record SubscriptionIdResponse(String subscriptionId) {
    public static SubscriptionIdResponse from(RegisterSubscriptionResult result) {
        return new SubscriptionIdResponse(result.subscriptionId());
    }
}
