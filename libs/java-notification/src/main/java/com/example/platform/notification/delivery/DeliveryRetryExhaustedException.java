package com.example.platform.notification.delivery;

/**
 * {@code attemptCount} has reached {@code maxAttempts} and the last attempt failed
 * transiently. The delivery has moved to terminal {@link DeliveryStatus#FAILED}
 * before this is thrown (the transition is applied first, exactly as the wms
 * Category-C reference does, so the caller can persist the terminal state on catch).
 *
 * <p>Lifted from {@code com.wms.notification.domain.error.DeliveryRetryExhaustedException}.
 * The lib uses an opaque {@code String deliveryId} rather than a UUID so it does not
 * constrain the service's id type.
 */
public final class DeliveryRetryExhaustedException extends RuntimeException {

    public static final String CODE = "DELIVERY_RETRY_EXHAUSTED";

    private final String deliveryId;
    private final int attempts;

    public DeliveryRetryExhaustedException(String deliveryId, int attempts) {
        super("Retry budget exhausted for delivery " + deliveryId + " after " + attempts + " attempts");
        this.deliveryId = deliveryId;
        this.attempts = attempts;
    }

    public String deliveryId() {
        return deliveryId;
    }

    public int attempts() {
        return attempts;
    }
}
