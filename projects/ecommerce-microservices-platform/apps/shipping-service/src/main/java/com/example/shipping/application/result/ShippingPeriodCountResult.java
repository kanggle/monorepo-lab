package com.example.shipping.application.result;

public record ShippingPeriodCountResult(
        long today,
        long week,
        long month,
        long total
) {}
