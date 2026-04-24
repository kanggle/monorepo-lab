package com.example.shipping.application.command;

public record CreateShippingCommand(
        String orderId,
        String userId
) {}
