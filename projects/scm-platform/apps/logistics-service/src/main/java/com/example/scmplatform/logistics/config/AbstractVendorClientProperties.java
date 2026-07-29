package com.example.scmplatform.logistics.config;

/**
 * The settings every carrier-aggregator vendor client needs (external-integrations.md §1.4/§1.8,
 * §2.4/§2.8): base url, API key, the two mandatory timeouts (I1), and the <b>dedicated</b> pool
 * sizing (I9).
 *
 * <p>Not itself a {@code @ConfigurationProperties} class — each vendor's subclass carries its own
 * {@code prefix} and its own defaults, so {@code logistics.easypost.*} and
 * {@code logistics.goodsflow.*} remain two independent binding roots producing two independent
 * pools. Spring Boot's JavaBean binding walks inherited setters, so every field below binds under
 * either prefix (relaxed binding included).
 *
 * <p>Sharing the shape shares <b>no runtime instance</b>: {@code VendorHttpClientFactory} is
 * invoked once per vendor {@code @Bean}, and each invocation builds its own
 * {@code PoolingHttpClientConnectionManager}.
 */
public abstract class AbstractVendorClientProperties {

    /** Vendor API base url. Each subclass sets its own default in its constructor. */
    private String baseUrl;

    /** Vendor API key. How it is presented (Basic vs header) is the vendor adapter's business. */
    private String apiKey;

    /** I1 — hostname resolve + TCP handshake. Never rely on the client default. */
    private int connectTimeoutSeconds = 5;

    /** I1 — read/response timeout. */
    private int readTimeoutSeconds = 30;

    /** Dedicated pool sizing — not shared with any other vendor (I9). */
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
