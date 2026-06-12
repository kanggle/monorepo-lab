package com.example.erp.notification.application;

import com.example.erp.notification.config.ExternalNotificationProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.random.RandomGenerator;

/**
 * Category C retry backoff (ADR-MONO-005 § D5; TASK-ERP-BE-020): exponential
 * {@code initialBackoff · 2^(attempt-1)} capped at {@code maxBackoff}, with <b>±20%
 * jitter</b> (to de-correlate concurrent retries). The {@link RandomGenerator} is
 * injected so unit tests are deterministic.
 */
@Component
public class RetryBackoffPolicy {

    private static final double JITTER = 0.2;

    private final long initialBackoffMs;
    private final long maxBackoffMs;
    private final RandomGenerator random;

    @Autowired
    public RetryBackoffPolicy(ExternalNotificationProperties properties, RandomGenerator random) {
        this(properties.getRetry().getInitialBackoffMs(),
                properties.getRetry().getMaxBackoffMs(), random);
    }

    /** Direct constructor for unit tests (deterministic {@code random}). */
    public RetryBackoffPolicy(long initialBackoffMs, long maxBackoffMs, RandomGenerator random) {
        this.initialBackoffMs = initialBackoffMs;
        this.maxBackoffMs = maxBackoffMs;
        this.random = random;
    }

    /**
     * Backoff to wait after the {@code attemptNumber}-th failed attempt (1-based):
     * {@code initial·2^(attemptNumber-1)} capped at {@code maxBackoff}, then a jitter
     * factor in {@code [0.8, 1.2)}.
     */
    public Duration backoffFor(int attemptNumber) {
        int exponent = Math.max(0, attemptNumber - 1);
        // Cap the shift to avoid long overflow on a pathological attempt number.
        long base = exponent >= 62
                ? maxBackoffMs
                : Math.min(maxBackoffMs, initialBackoffMs << exponent);
        double factor = (1.0 - JITTER) + random.nextDouble() * (2.0 * JITTER);
        long jittered = Math.round(base * factor);
        return Duration.ofMillis(Math.max(0, jittered));
    }
}
