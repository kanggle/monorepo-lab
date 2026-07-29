package com.example.scmplatform.logistics.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * EasyPost client configuration (external-integrations.md §1.1/§1.2/§1.4/§1.8). Bound from
 * {@code logistics.easypost.*} — its own binding root, independent of 굿스플로's. The
 * {@code apiKey} is the HTTP Basic username (empty password); a missing/invalid key surfaces as
 * {@code DISPATCH_FAILED} on first call, never a boot failure.
 *
 * <p>Timeouts and the dedicated pool sizing are inherited from
 * {@link AbstractVendorClientProperties}; Spring Boot binds inherited setters, so every
 * {@code logistics.easypost.*} key (relaxed binding included) still populates.
 */
@ConfigurationProperties(prefix = "logistics.easypost")
public class EasyPostClientProperties extends AbstractVendorClientProperties {

    public EasyPostClientProperties() {
        /* {@code https://api.easypost.com/v2} — test vs prod is chosen by which key is supplied. */
        setBaseUrl("https://api.easypost.com/v2");
    }
}
