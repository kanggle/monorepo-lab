package com.wms.inventory.application.port.out;

import java.util.Optional;
import java.util.UUID;

/**
 * Source for the low-stock threshold per {@code (warehouseId, skuId)} pair.
 *
 * <p>v1 simplification: implementations may return {@link Optional#empty()}
 * when the setting is absent — low-stock detection is then disabled for that
 * row. The {@code admin-service} will eventually own the
 * {@code admin.settings} subscription pipeline; this port is a pluggable
 * seam so the integration can land later without rewriting the alert path.
 */
public interface LowStockThresholdPort {

    /**
     * Resolve the threshold for the supplied warehouse/sku combo. Returns
     * empty when no threshold is configured.
     */
    Optional<Integer> findThreshold(UUID warehouseId, UUID skuId);
}
