package com.example.erp.notification.application.port.outbound;

import com.example.erp.notification.domain.delivery.DeliveryChannel;
import com.example.erp.notification.domain.notification.Notification;

/**
 * Outbound delivery boundary — the swappable channel port. v1 ships an
 * {@code IN_APP} adapter (delivery = the in-app persist itself, immediately
 * DELIVERED) and a no-op stub external adapter. The v2 external channel
 * (Slack/SMTP) + its Category C retry scheduler bind against this same port
 * without touching the domain (wms notification-service {@code ChannelPort}
 * precedent).
 *
 * <p><b>Green-wash discipline</b>: the stub external adapter MUST NOT report a
 * successful external delivery — it logs only and returns
 * {@link DeliveryOutcome#noop()} (no DELIVERED claim). Only the IN_APP adapter
 * reports {@link DeliveryOutcome#delivered()}.
 */
public interface NotificationChannelPort {

    /** The channel this adapter delivers on. */
    DeliveryChannel channel();

    /**
     * Attempts to deliver the (already-persisted) notification on this channel.
     * For IN_APP this is a synchronous success (the persist itself); for the
     * stub external adapter it is a no-op (never claims external delivery).
     */
    DeliveryOutcome deliver(Notification notification);

    /** The outcome of a delivery attempt. */
    record DeliveryOutcome(boolean delivered, String detail) {

        /** IN_APP success — the notification is delivered. */
        public static DeliveryOutcome ofDelivered() {
            return new DeliveryOutcome(true, null);
        }

        /** Stub external no-op — NOT delivered (green-wash forbidden). */
        public static DeliveryOutcome noop() {
            return new DeliveryOutcome(false, "external channel not implemented (v2)");
        }
    }
}
