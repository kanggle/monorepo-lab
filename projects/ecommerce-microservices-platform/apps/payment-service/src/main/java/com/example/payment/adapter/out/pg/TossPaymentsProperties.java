package com.example.payment.adapter.out.pg;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "toss.payments")
public record TossPaymentsProperties(
        String secretKey,
        String baseUrl
) {
}
