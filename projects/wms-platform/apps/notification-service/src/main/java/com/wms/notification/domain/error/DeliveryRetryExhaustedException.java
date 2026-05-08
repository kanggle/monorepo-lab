package com.wms.notification.domain.error;

import java.util.UUID;

/**
 * {@code attemptCount} has reached {@code maxAttempts} (5 in v1) and the
 * last attempt failed. The delivery moves to terminal {@code FAILED}.
 */
public final class DeliveryRetryExhaustedException extends NotificationDomainException {

    public static final String CODE = "DELIVERY_RETRY_EXHAUSTED";

    private final UUID deliveryId;
    private final int attempts;

    public DeliveryRetryExhaustedException(UUID deliveryId, int attempts) {
        super(CODE, "Retry budget exhausted for delivery " + deliveryId + " after " + attempts + " attempts");
        this.deliveryId = deliveryId;
        this.attempts = attempts;
    }

    public UUID deliveryId() {
        return deliveryId;
    }

    public int attempts() {
        return attempts;
    }
}
