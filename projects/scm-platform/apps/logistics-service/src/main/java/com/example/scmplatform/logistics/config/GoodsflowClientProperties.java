package com.example.scmplatform.logistics.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 굿스플로 client configuration (external-integrations.md §2.1/§2.2/§2.4/§2.8). Bound from
 * {@code logistics.goodsflow.*}. Auth is an <b>API-key header</b> (not HTTP Basic like EasyPost) —
 * the {@code apiKey} is sent under {@code apiKeyHeaderName}. A missing/invalid key surfaces as
 * {@code DISPATCH_FAILED} on first call, never a boot failure; EasyPost is unaffected (per-vendor
 * keys/pools, §6). The pool + resilience instances are dedicated to 굿스플로 (I9).
 */
@ConfigurationProperties(prefix = "logistics.goodsflow")
public class GoodsflowClientProperties {

    /** {@code https://test-api.goodsflow.io} for stg/dev; production is a distinct host (§2.1). */
    private String baseUrl = "https://test-api.goodsflow.io";

    /** 굿스플로 API key (§2.2). Sent as the value of {@link #apiKeyHeaderName}. */
    private String apiKey;

    /**
     * The vendor-specified API-key header name (§2.2 — confirmed against the 굿스플로 OPEN API at
     * implementation). Configurable so the exact header can be set without a code change.
     */
    private String apiKeyHeaderName = "Authorization";

    private int connectTimeoutSeconds = 5;
    private int readTimeoutSeconds = 30;

    /** Dedicated pool sizing — not shared with any other vendor (I9, §2.8). */
    private int poolMaxTotal = 10;
    private int poolMaxPerRoute = 10;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getApiKeyHeaderName() {
        return apiKeyHeaderName;
    }

    public void setApiKeyHeaderName(String apiKeyHeaderName) {
        this.apiKeyHeaderName = apiKeyHeaderName;
    }

    public int getConnectTimeoutSeconds() {
        return connectTimeoutSeconds;
    }

    public void setConnectTimeoutSeconds(int connectTimeoutSeconds) {
        this.connectTimeoutSeconds = connectTimeoutSeconds;
    }

    public int getReadTimeoutSeconds() {
        return readTimeoutSeconds;
    }

    public void setReadTimeoutSeconds(int readTimeoutSeconds) {
        this.readTimeoutSeconds = readTimeoutSeconds;
    }

    public int getPoolMaxTotal() {
        return poolMaxTotal;
    }

    public void setPoolMaxTotal(int poolMaxTotal) {
        this.poolMaxTotal = poolMaxTotal;
    }

    public int getPoolMaxPerRoute() {
        return poolMaxPerRoute;
    }

    public void setPoolMaxPerRoute(int poolMaxPerRoute) {
        this.poolMaxPerRoute = poolMaxPerRoute;
    }
}
