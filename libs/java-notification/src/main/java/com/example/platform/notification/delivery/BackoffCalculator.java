package com.example.platform.notification.delivery;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Computes the next retry time for a transiently-failed delivery using a
 * list-indexed backoff schedule with ±jitter — the exact arithmetic lifted from
 * the wms Category-C reference ({@code DeliveryDispatchPerRow#nextBackoff}).
 *
 * <p>Defaults match the reference: schedule {@code [1, 5, 30, 120, 600]} seconds,
 * jitter ratio {@code 0.2} (±20%), max attempts {@code 5}. The base delay for an
 * attempt is {@code scheduleSeconds[min(attemptCount, size-1)]} (the last entry
 * clamps for attempts beyond the schedule length), then offset by a uniform jitter
 * in {@code [-base*ratio, +base*ratio]}, floored at zero.
 *
 * <p>Deterministic-testable: the jitter is drawn from an injected
 * {@link JitterSource}. {@link #NO_JITTER} yields the exact base delay, and a fixed
 * source lets a test assert precise bounds. The default constructor uses
 * {@link ThreadLocalRandom}, matching the reference's runtime behaviour.
 */
public final class BackoffCalculator {

    /** Default backoff schedule (seconds) — {@code 1s → 5s → 30s → 2m → 10m}. */
    public static final List<Integer> DEFAULT_BACKOFF_SECONDS = List.of(1, 5, 30, 120, 600);
    public static final double DEFAULT_JITTER_RATIO = 0.2;
    public static final int DEFAULT_MAX_ATTEMPTS = 5;

    /**
     * Source of a uniform jitter fraction in {@code [-1, +1]}; multiplied by
     * {@code base * jitterRatio} to produce the offset.
     */
    @FunctionalInterface
    public interface JitterSource {
        /** @return a fraction in {@code [-1.0, +1.0]}. */
        double nextFraction();
    }

    /** Deterministic zero-jitter source — returns the exact base delay. */
    public static final JitterSource NO_JITTER = () -> 0.0;

    private final List<Integer> backoffSeconds;
    private final double jitterRatio;
    private final int maxAttempts;
    private final JitterSource jitterSource;

    /** Construct with all defaults and {@link ThreadLocalRandom} jitter. */
    public BackoffCalculator() {
        this(DEFAULT_BACKOFF_SECONDS, DEFAULT_JITTER_RATIO, DEFAULT_MAX_ATTEMPTS,
                () -> ThreadLocalRandom.current().nextDouble(-1.0, 1.0 + 1e-9));
    }

    public BackoffCalculator(List<Integer> backoffSeconds,
                             double jitterRatio,
                             int maxAttempts,
                             JitterSource jitterSource) {
        this.backoffSeconds = List.copyOf(Objects.requireNonNull(backoffSeconds, "backoffSeconds"));
        if (this.backoffSeconds.isEmpty()) {
            throw new IllegalArgumentException("backoffSeconds must not be empty");
        }
        if (jitterRatio < 0) {
            throw new IllegalArgumentException("jitterRatio must be >= 0");
        }
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts must be > 0");
        }
        this.jitterRatio = jitterRatio;
        this.maxAttempts = maxAttempts;
        this.jitterSource = Objects.requireNonNull(jitterSource, "jitterSource");
    }

    /** The configured retry budget (terminal once {@code attemptCount >= maxAttempts}). */
    public int maxAttempts() {
        return maxAttempts;
    }

    /**
     * The jittered next-attempt delay for the given attempt count (the value used by
     * the reference's {@code nextBackoff}).
     *
     * @param attemptCount attempts already made (0 = computing the delay after the first failure)
     */
    public Duration backoff(int attemptCount) {
        long baseSeconds = baseSeconds(attemptCount);
        double jitterMagnitude = baseSeconds * jitterRatio;
        double offset = jitterSource.nextFraction() * jitterMagnitude;
        long jitteredMillis = Math.max(0L, (long) ((baseSeconds + offset) * 1000));
        return Duration.ofMillis(jitteredMillis);
    }

    /**
     * The absolute next-retry time = {@code now + backoff(attemptCount)}.
     */
    public Instant nextRetryAt(int attemptCount, Instant now) {
        return Objects.requireNonNull(now, "now").plus(backoff(attemptCount));
    }

    /** The list-indexed base delay (seconds), no jitter — clamps at the last entry. */
    public long baseSeconds(int attemptCount) {
        int idx = Math.min(Math.max(attemptCount, 0), backoffSeconds.size() - 1);
        return backoffSeconds.get(idx);
    }
}
