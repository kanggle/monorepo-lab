package com.wms.notification.domain.error;

import com.wms.notification.domain.delivery.DeliveryStatus;

/**
 * Application code attempted a forbidden state transition (e.g. terminal →
 * any other state). Surfaces a programmer error, not an environmental one.
 * T4 of the {@code transactional} trait.
 */
public final class DeliveryStateTransitionInvalidException extends NotificationDomainException {

    public static final String CODE = "DELIVERY_STATE_TRANSITION_INVALID";

    private final DeliveryStatus from;
    private final DeliveryStatus to;

    public DeliveryStateTransitionInvalidException(DeliveryStatus from, DeliveryStatus to) {
        super(CODE, "Illegal delivery state transition: " + from + " → " + to);
        this.from = from;
        this.to = to;
    }

    public DeliveryStatus from() {
        return from;
    }

    public DeliveryStatus to() {
        return to;
    }
}
