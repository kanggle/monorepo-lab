package com.example.shipping.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * Configuration for the cross-project fulfillment forward leg (ADR-MONO-022 §D7).
 *
 * @param enabled            master switch for publishing {@code ecommerce.fulfillment.requested.v1}.
 *                           {@code false} disables publish (D8 standalone degradation).
 * @param defaultWarehouseCode default wms warehouse code carried on the fulfillment payload (D3).
 * @param requireSkuMapping  when {@code true}, a SKU absent from {@code skuMap} blocks fulfillment
 *                           for that order (log + alert, no publish). When {@code false} (default),
 *                           unmapped SKUs pass through with identity mapping.
 * @param skuMap             ecommerce SKU → wms SKU code map. Empty by default (identity passthrough).
 */
@ConfigurationProperties(prefix = "fulfillment")
public record FulfillmentProperties(
        boolean enabled,
        String defaultWarehouseCode,
        boolean requireSkuMapping,
        Map<String, String> skuMap
) {
    public FulfillmentProperties {
        skuMap = skuMap == null ? Map.of() : Map.copyOf(skuMap);
    }
}
