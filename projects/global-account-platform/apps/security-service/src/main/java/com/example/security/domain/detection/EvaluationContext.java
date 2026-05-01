package com.example.security.domain.detection;

import java.time.Instant;

/**
 * Context for rule evaluation. Derived from the consumed Kafka event.
 * Pure domain type — no framework imports.
 */
public record EvaluationContext(
        String tenantId,
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
     * Also defaults {@code tenantId} to null — TASK-BE-248 Phase 2 will require
     * non-null tenantId at construction time for production paths; tests still
     * use this overload.
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
        this(null, eventId, eventType, accountId, ipMasked, deviceFingerprint, geoCountry,
                occurredAt, failCount, null, null);
    }

    /**
     * Tenant-aware constructor for callers that have tenantId but no device fields.
     */
    public EvaluationContext(
            String tenantId,
            String eventId,
            String eventType,
            String accountId,
            String ipMasked,
            String deviceFingerprint,
            String geoCountry,
            Instant occurredAt,
            Integer failCount) {
        this(tenantId, eventId, eventType, accountId, ipMasked, deviceFingerprint, geoCountry,
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
