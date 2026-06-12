package com.example.finance.ledger.application.port.outbound;

import java.time.Instant;

/**
 * Outbound port for the current instant. Use cases depend on this rather than
 * {@link java.time.Clock} directly so unit tests inject a fixed instant without
 * a Spring context.
 */
@FunctionalInterface
public interface ClockPort {
    Instant now();
}
