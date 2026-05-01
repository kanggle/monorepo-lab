package com.example.security.domain.detection;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * DeviceChangeRule — fires on a successful login from a device fingerprint
 * that has not been seen for the account within the Redis TTL window (90 days).
 *
 * <p>Score is fixed at {@link DetectionThresholds#deviceChangeScore()} (default
 * 50 → ALERT only). This prevents false-positive lockouts when a user switches
 * devices. Combined with another rule, the aggregator's {@code max} elevates
 * the total score to AUTO_LOCK when appropriate.</p>
 *
 * <p>Regardless of firing, the device fingerprint is added to the known-device
 * set so the next login from the same device does not fire.</p>
 */
public class DeviceChangeRule implements SuspiciousActivityRule {

    public static final String CODE = "DEVICE_CHANGE";

    private final KnownDeviceStore store;
    private final DetectionThresholds thresholds;

    public DeviceChangeRule(KnownDeviceStore store, DetectionThresholds thresholds) {
        this.store = store;
        this.thresholds = thresholds;
    }

    @Override
    public String ruleCode() {
        return CODE;
    }

    @Override
    public DetectionResult evaluate(EvaluationContext ctx) {
        if (ctx == null || !ctx.isLoginSucceeded() || !ctx.hasAccount()) {
            return DetectionResult.NONE;
        }
        if (!thresholds.deviceAlertOnNew()) {
            return DetectionResult.NONE;
        }

        // TASK-BE-025 primary path: auth-service tells us whether device_sessions row
        // was newly created in this login. Trust the authoritative server-side signal
        // instead of fingerprint churn (browser updates, UA drift, etc.).
        if (ctx.isNewDevice() != null) {
            if (!ctx.isNewDevice()) {
                return DetectionResult.NONE;
            }
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("description", "Login from newly registered device_session");
            if (ctx.deviceId() != null) {
                evidence.put("deviceId", ctx.deviceId());
            }
            return new DetectionResult(CODE, thresholds.deviceChangeScore(), evidence);
        }

        // Fallback (legacy events without isNewDevice) — fingerprint comparison.
        String fp = ctx.deviceFingerprint();
        if (fp == null || fp.isBlank()) {
            return DetectionResult.NONE;
        }
        // TASK-BE-248 Phase 2a: use tenantId in store key for per-tenant device isolation.
        String tenantId = ctx.tenantId() != null ? ctx.tenantId() : "";
        boolean known = store.isKnown(tenantId, ctx.accountId(), fp);
        // Always remember the device, even when it fires — next time it won't fire.
        store.remember(tenantId, ctx.accountId(), fp);

        if (known) {
            return DetectionResult.NONE;
        }
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("description", "Login from previously unseen device");
        evidence.put("deviceFingerprintSuffix", suffix(fp));
        return new DetectionResult(CODE, thresholds.deviceChangeScore(), evidence);
    }

    private static String suffix(String fp) {
        return fp.length() <= 8 ? fp : fp.substring(fp.length() - 8);
    }
}
