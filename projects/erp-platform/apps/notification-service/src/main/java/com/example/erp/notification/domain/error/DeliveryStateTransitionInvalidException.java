package com.example.erp.notification.domain.error;

import com.example.erp.notification.domain.delivery.DeliveryStatus;

/**
 * Raised when an illegal delivery state transition is attempted (e.g. mutating a
 * terminal {@code DELIVERED}/{@code FAILED} delivery). architecture.md
 * § Delivery model (terminal-immutability).
 */
public class DeliveryStateTransitionInvalidException extends NotificationDomainException {

    public static final String CODE = "DELIVERY_STATE_TRANSITION_INVALID";

    public DeliveryStateTransitionInvalidException(DeliveryStatus from, DeliveryStatus to) {
        super("Illegal delivery transition " + from + " -> " + to);
    }
}
