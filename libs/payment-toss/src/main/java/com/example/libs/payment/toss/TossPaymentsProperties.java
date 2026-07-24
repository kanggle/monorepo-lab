package com.example.libs.payment.toss;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Toss Payments adapter configuration (project-agnostic; relocated from payment-service by
 * ADR-MONO-056 Phase 1). Domain-free — only vendor endpoint + credential + timeouts.
 */
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
