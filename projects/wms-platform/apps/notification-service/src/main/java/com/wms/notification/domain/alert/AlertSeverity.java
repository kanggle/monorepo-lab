package com.wms.notification.domain.alert;

/**
 * Severity levels passed through to channel templates for visual emphasis.
 * Orthogonal to delivery outcome.
 *
 * <p>Order matters — {@link #isAtLeast(AlertSeverity)} uses the declaration
 * order so {@code CRITICAL > WARNING > INFO}.
 */
public enum AlertSeverity {

    INFO,
    WARNING,
    CRITICAL;

    /** {@code true} when {@code this} severity is &gt;= {@code threshold}. */
    public boolean isAtLeast(AlertSeverity threshold) {
        return this.ordinal() >= threshold.ordinal();
    }
}
