package com.example.product.domain.exception;

import java.util.UUID;

public class VariantNotFoundException extends RuntimeException {

    public VariantNotFoundException(UUID variantId) {
        super("Variant not found: " + variantId);
    }
}
