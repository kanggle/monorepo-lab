package com.example.scmplatform.logistics.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 굿스플로 client configuration (external-integrations.md §2.1/§2.2/§2.4/§2.8). Bound from
 * {@code logistics.goodsflow.*} — its own binding root, independent of EasyPost's. Auth is an
 * <b>API-key header</b> (not HTTP Basic like EasyPost) — the {@code apiKey} is sent under
 * {@code apiKeyHeaderName}. A missing/invalid key surfaces as {@code DISPATCH_FAILED} on first
 * call, never a boot failure; EasyPost is unaffected (per-vendor keys/pools, §6). The pool +
 * resilience instances are dedicated to 굿스플로 (I9).
 *
 * <p>Timeouts and the dedicated pool sizing are inherited from
 * {@link AbstractVendorClientProperties}; {@link #apiKeyHeaderName} is 굿스플로-only and stays
 * here.
 */
@ConfigurationProperties(prefix = "logistics.goodsflow")
public class GoodsflowClientProperties extends AbstractVendorClientProperties {

    /**
     * The vendor-specified API-key header name (§2.2 — confirmed against the 굿스플로 OPEN API at
     * implementation). Configurable so the exact header can be set without a code change.
     * 굿스플로-only: EasyPost authenticates with HTTP Basic and has no such property.
     */
    private String apiKeyHeaderName = "Authorization";

    public GoodsflowClientProperties() {
        /* {@code https://test-api.goodsflow.io} for stg/dev; production is a distinct host (§2.1). */
        setBaseUrl("https://test-api.goodsflow.io");
    }

    public String getApiKeyHeaderName() {
        return apiKeyHeaderName;
    }

    public void setApiKeyHeaderName(String apiKeyHeaderName) {
        this.apiKeyHeaderName = apiKeyHeaderName;
    }
}
