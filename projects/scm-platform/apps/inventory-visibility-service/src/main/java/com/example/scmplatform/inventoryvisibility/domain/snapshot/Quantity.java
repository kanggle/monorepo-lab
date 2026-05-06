package com.example.scmplatform.inventoryvisibility.domain.snapshot;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Value object wrapping a non-negative inventory quantity (BigDecimal, 3 decimal places).
 */
public record Quantity(BigDecimal value) {

    public static final Quantity ZERO = new Quantity(BigDecimal.ZERO);

    public Quantity {
        Objects.requireNonNull(value, "quantity value must not be null");
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("quantity must not be negative: " + value);
        }
    }

    public static Quantity of(BigDecimal value) {
        return new Quantity(value);
    }

    public static Quantity of(long value) {
        return new Quantity(BigDecimal.valueOf(value));
    }

    public Quantity add(Quantity other) {
        return new Quantity(this.value.add(other.value));
    }

    public Quantity subtract(Quantity other) {
        BigDecimal result = this.value.subtract(other.value);
        return new Quantity(result.max(BigDecimal.ZERO)); // floor at zero
    }

    @Override
    public String toString() {
        return value.toPlainString();
    }
}
