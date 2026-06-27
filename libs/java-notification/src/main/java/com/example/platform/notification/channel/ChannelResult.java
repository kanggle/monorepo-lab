package com.example.platform.notification.channel;

/**
 * Outcome of a single {@link NotificationChannelAdapter#deliver(ChannelDeliveryRequest)}
 * attempt. A channel adapter <b>never throws</b>; every failure is encoded here so the
 * delivery engine can apply the right state transition (the wms Category-C reference
 * distinguished permanent vs retryable failures via typed exceptions — this lib carries
 * that distinction as a value instead).
 *
 * <ul>
 *   <li>{@link #delivered} {@code true} — vendor accepted; {@link #ref} carries the success ref,
 *       {@link #error} is {@code null}.</li>
 *   <li>{@link #delivered} {@code false} + {@link #permanent} {@code true} — a non-retryable
 *       failure (vendor 4xx / misconfigured channel); the engine fails the delivery without
 *       consuming further retry budget.</li>
 *   <li>{@link #delivered} {@code false} + {@link #permanent} {@code false} — a transient failure
 *       (5xx / IO / timeout); the engine schedules a backoff retry until the budget is exhausted.</li>
 * </ul>
 *
 * @param delivered whether the vendor accepted the message
 * @param permanent when {@code !delivered}: {@code true} = do-not-retry, {@code false} = transient
 * @param ref       success reference (vendor message id, etc.); {@code null} on failure
 * @param error     failure detail; {@code null} on success
 */
public record ChannelResult(
        boolean delivered,
        boolean permanent,
        String ref,
        String error
) {
    /** Vendor accepted the message. */
    public static ChannelResult delivered(String ref) {
        return new ChannelResult(true, false, ref, null);
    }

    /** Transient failure (5xx / IO / timeout) — the engine should schedule a retry. */
    public static ChannelResult transientFailure(String error) {
        return new ChannelResult(false, false, null, error);
    }

    /** Permanent failure (4xx / misconfigured) — the engine should fail without retrying. */
    public static ChannelResult permanentFailure(String error) {
        return new ChannelResult(false, true, null, error);
    }
}
