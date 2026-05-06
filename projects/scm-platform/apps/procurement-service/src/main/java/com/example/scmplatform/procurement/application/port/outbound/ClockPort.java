package com.example.scmplatform.procurement.application.port.outbound;

import java.time.Instant;

/**
 * Test-friendly clock abstraction. Use cases depend on this rather than
 * {@link java.time.Clock} directly so unit tests can inject a fixed instant
 * without Spring context.
 */
public interface ClockPort {
    Instant now();
}
