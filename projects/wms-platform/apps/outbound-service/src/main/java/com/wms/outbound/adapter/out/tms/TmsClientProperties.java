package com.wms.outbound.adapter.out.tms;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalised configuration for the TMS HTTP client. Bound from
 * {@code application.yml} key {@code outbound.tms.*}.
 *
 * @param baseUrl          base URL of the TMS API (e.g.
 *                         {@code https://tms.example.com/api/v1})
 * @param apiKey           value sent in {@code X-Tms-Api-Key} header; loaded
 *                         from Secret Manager in prod
 * @param connectTimeoutMs TCP connect timeout (I1: 5s default)
 * @param readTimeoutMs    socket read timeout (I1: 30s default)
 * @param maxConnections   connection-pool size (I9: 10 default — must match
 *                         the Resilience4j ThreadPoolBulkhead size)
 */
@ConfigurationProperties(prefix = "outbound.tms")
public record TmsClientProperties(
        String baseUrl,
        String apiKey,
        long connectTimeoutMs,
        long readTimeoutMs,
        int maxConnections) {

    public TmsClientProperties {
        if (connectTimeoutMs <= 0) {
            connectTimeoutMs = 5_000L;
        }
        if (readTimeoutMs <= 0) {
            readTimeoutMs = 30_000L;
        }
        if (maxConnections <= 0) {
            maxConnections = 10;
        }
    }
}
