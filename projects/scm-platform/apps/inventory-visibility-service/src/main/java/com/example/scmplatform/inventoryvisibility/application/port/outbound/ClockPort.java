package com.example.scmplatform.inventoryvisibility.application.port.outbound;

import java.time.Instant;

/**
 * Outbound port for current time. Allows test substitution of system clock.
 */
@FunctionalInterface
public interface ClockPort {
    Instant now();
}
