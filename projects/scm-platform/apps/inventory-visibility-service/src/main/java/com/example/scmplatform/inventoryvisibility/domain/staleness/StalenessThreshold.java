package com.example.scmplatform.inventoryvisibility.domain.staleness;

import java.time.Duration;
import java.util.Objects;

/**
 * Value object representing the staleness threshold duration.
 * Default is 10 minutes per inventory-visibility.staleness.threshold-seconds config.
 */
public record StalenessThreshold(Duration value) {

    public static final StalenessThreshold DEFAULT = new StalenessThreshold(Duration.ofMinutes(10));

    public StalenessThreshold {
        Objects.requireNonNull(value, "threshold duration must not be null");
        if (value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException("staleness threshold must be positive");
        }
    }

    public static StalenessThreshold ofSeconds(long seconds) {
        return new StalenessThreshold(Duration.ofSeconds(seconds));
    }

    public Duration getDuration() {
        return value;
    }
}
