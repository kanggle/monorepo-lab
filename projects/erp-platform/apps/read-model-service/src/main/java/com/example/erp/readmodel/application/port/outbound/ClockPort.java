package com.example.erp.readmodel.application.port.outbound;

import java.time.Instant;

/**
 * Outbound port for the current instant. Allows test substitution of the
 * system clock (keeps {@code java.time.Clock} wiring out of the application
 * layer).
 */
@FunctionalInterface
public interface ClockPort {
    Instant now();
}
