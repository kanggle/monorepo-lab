package com.example.notification.application.port.out;

import com.example.notification.domain.model.PushSubscription;

/**
 * Outbound port to the Web Push provider (TASK-BE-464). Isolates the VAPID signing +
 * HTTP delivery library behind an interface so the application/adapter logic stays
 * testable and the concrete library is swappable (per architecture.md — external channel
 * adapters behind outbound ports).
 */
public interface WebPushGateway {

    /** Whether a VAPID keypair is configured; when false, push delivery is skipped. */
    boolean isConfigured();

    /** The VAPID public key clients use to subscribe, or {@code null} when not configured. */
    String publicKey();

    /**
     * Deliver an encrypted payload to one subscription endpoint. Returns the push service
     * HTTP status (so the caller can prune expired 404/410 subscriptions).
     *
     * @throws WebPushDeliveryException on a transport/crypto failure (no HTTP status obtained)
     */
    WebPushSendResult send(PushSubscription subscription, byte[] payload);
}
