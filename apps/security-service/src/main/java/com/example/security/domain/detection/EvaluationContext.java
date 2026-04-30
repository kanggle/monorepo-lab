package com.example.security.domain.detection;

import java.time.Instant;

/**
 * Context for rule evaluation. Derived from the consumed Kafka event.
 * Pure domain type — no framework imports.
 */
public record EvaluationContext(
        String eventId,
        String eventType,
        String accountId,
        String ipMasked,
        String deviceFingerprint,
        String geoCountry,
        Instant occurredAt,
        Integer failCount,
        String deviceId,
        Boolean isNewDevice
) {

    /**
     * Backward-compatible constructor for call sites that predate the TASK-BE-025
     * device_id payload extension. Defaults {@code deviceId} and {@code isNewDevice}
     * to null so legacy tests and rule evaluation fall back to the fingerprint path.
     */
    public EvaluationContext(
            String eventId,
            String eventType,
            String accountId,
            String ipMasked,
            String deviceFingerprint,
            String geoCountry,
            Instant occurredAt,
            Integer failCount) {
        this(eventId, eventType, accountId, ipMasked, deviceFingerprint, geoCountry,
                occurredAt, failCount, null, null);
    }

    public boolean hasAccount() {
        return accountId != null && !accountId.isBlank();
    }

    public boolean isLoginFailed() {
        return "auth.login.failed".equals(eventType);
    }

    public boolean isLoginSucceeded() {
        return "auth.login.succeeded".equals(eventType);
    }

    public boolean isTokenReuseDetected() {
        return "auth.token.reuse.detected".equals(eventType);
    }
}
