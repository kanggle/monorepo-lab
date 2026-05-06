package com.example.scmplatform.inventoryvisibility.domain.snapshot;

import java.util.Objects;

/**
 * Value object for a Stock-Keeping Unit identifier.
 */
public record Sku(String value) {

    public Sku {
        Objects.requireNonNull(value, "sku value must not be null");
        if (value.isBlank()) throw new IllegalArgumentException("sku value must not be blank");
    }

    public static Sku of(String value) {
        return new Sku(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
