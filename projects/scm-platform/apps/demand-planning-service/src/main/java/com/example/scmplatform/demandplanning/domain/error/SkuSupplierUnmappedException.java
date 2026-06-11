package com.example.scmplatform.demandplanning.domain.error;

/**
 * Raised (non-retryable) when a SKU has no supplier mapping at alert time.
 * Fail-closed: event goes to DLT + ops alert. Maps to HTTP 422 SKU_SUPPLIER_UNMAPPED.
 */
public class SkuSupplierUnmappedException extends RuntimeException {
    public SkuSupplierUnmappedException(String skuCode) {
        super("No supplier mapping for skuCode: " + skuCode);
    }
}
