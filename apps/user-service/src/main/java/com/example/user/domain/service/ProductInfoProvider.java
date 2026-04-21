package com.example.user.domain.service;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Domain interface for fetching product information from product-service.
 * Infrastructure layer provides the implementation.
 */
public interface ProductInfoProvider {

    record ProductInfo(UUID productId, String name, int price, String status) {
    }

    /**
     * Fetches product information for the given product IDs.
     * Products that cannot be fetched (e.g., deleted, service unavailable) are returned with status "DELETED".
     */
    Map<UUID, ProductInfo> getProductInfos(Set<UUID> productIds);
}
