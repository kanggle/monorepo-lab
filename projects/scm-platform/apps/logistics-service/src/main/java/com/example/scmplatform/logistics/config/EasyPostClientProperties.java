package com.example.scmplatform.logistics.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * EasyPost client configuration (external-integrations.md §1.1/§1.2/§1.4/§1.8). Bound from
 * {@code logistics.easypost.*}. The {@code apiKey} is the HTTP Basic username (empty password);
 * a missing/invalid key surfaces as {@code DISPATCH_FAILED} on first call, never a boot failure.
 */
@ConfigurationProperties(prefix = "logistics.easypost")
public class EasyPostClientProperties {

    /** {@code https://api.easypost.com/v2} — test vs prod is chosen by which key is supplied. */
    private String baseUrl = "https://api.easypost.com/v2";

    /** EasyPost API key — the HTTP Basic username (empty password), §1.2. */
    private String apiKey;

    private int connectTimeoutSeconds = 5;
    private int readTimeoutSeconds = 30;

    /** Dedicated pool sizing — not shared with any other vendor (I9, §1.8). */
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
