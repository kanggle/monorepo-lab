package com.example.finance.account.application.port.outbound;

import java.time.Instant;

/**
 * Test-friendly clock abstraction. Use cases depend on this rather than
 * {@link java.time.Clock} directly so unit tests inject a fixed instant
 * without a Spring context.
 */
public interface ClockPort {
    Instant now();
}
