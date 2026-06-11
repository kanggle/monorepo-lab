package com.example.scmplatform.demandplanning.domain.error;

/**
 * Raised when a SKU→supplier mapping is not found. Maps to HTTP 404 MAPPING_NOT_FOUND.
 */
public class MappingNotFoundException extends RuntimeException {
    public MappingNotFoundException(String skuCode) {
        super("SKU-supplier mapping not found for skuCode: " + skuCode);
    }
}
