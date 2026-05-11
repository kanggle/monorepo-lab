package com.example.payment.adapter.out.pg;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "toss.payments")
public record TossPaymentsProperties(
        String secretKey,
        String baseUrl,
        Integer connectTimeoutMs,
        Integer readTimeoutMs
) {
    public TossPaymentsProperties {
        if (connectTimeoutMs == null) {
            connectTimeoutMs = 5000;
        }
        if (readTimeoutMs == null) {
            readTimeoutMs = 10000;
        }
    }
}
