package com.example.platform.notification.delivery;

/**
 * Application code attempted a forbidden delivery state transition (e.g. a
 * terminal record → any other state). A programmer error, not an environmental
 * one. Lifted from the wms Category-C reference.
 */
public final class DeliveryStateTransitionInvalidException extends RuntimeException {

    public static final String CODE = "DELIVERY_STATE_TRANSITION_INVALID";

    private final DeliveryStatus from;
    private final DeliveryStatus to;

    public DeliveryStateTransitionInvalidException(DeliveryStatus from, DeliveryStatus to) {
        super("Illegal delivery state transition: " + from + " → " + to);
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
