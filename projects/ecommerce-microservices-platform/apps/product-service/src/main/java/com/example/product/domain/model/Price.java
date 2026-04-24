package com.example.product.domain.model;

public record Price(long value) {

    public Price {
        if (value < 0) {
            throw new IllegalArgumentException("Price cannot be negative");
        }
    }

    public Price add(Price other) {
        return new Price(Math.addExact(this.value, other.value));
    }

    public Price subtract(Price other) {
        if (this.value < other.value) {
            throw new IllegalArgumentException(
                    "Cannot subtract " + other.value + " from " + this.value + ": result would be negative");
        }
        return new Price(this.value - other.value);
    }
}
