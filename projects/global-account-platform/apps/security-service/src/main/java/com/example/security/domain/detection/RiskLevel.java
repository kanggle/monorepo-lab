package com.example.security.domain.detection;

/**
 * Action threshold for detection results.
 *
 * <p>Thresholds defined by spec (specs/features/abnormal-login-detection.md):
 * <ul>
 *   <li>0-49 → {@link #NONE} — no record, no action</li>
 *   <li>50-79 → {@link #ALERT} — record in suspicious_events + emit detected event</li>
 *   <li>80-100 → {@link #AUTO_LOCK} — record + event + call account-service lock endpoint</li>
 * </ul>
 */
public enum RiskLevel {
    NONE,
    ALERT,
    AUTO_LOCK;

    public static RiskLevel fromScore(int score) {
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException("Risk score must be in [0, 100], got " + score);
        }
        if (score >= 80) {
            return AUTO_LOCK;
        }
        if (score >= 50) {
            return ALERT;
        }
        return NONE;
    }
}
