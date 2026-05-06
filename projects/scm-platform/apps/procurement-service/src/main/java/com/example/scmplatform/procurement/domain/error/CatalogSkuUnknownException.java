package com.example.scmplatform.procurement.domain.error;

/** Mapped to HTTP 422 {@code CATALOG_SKU_UNKNOWN}. */
public class CatalogSkuUnknownException extends RuntimeException {
    public CatalogSkuUnknownException(String message) {
        super(message);
    }
}
