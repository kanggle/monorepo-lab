package com.example.admin.application;

import com.example.admin.application.exception.ReasonRequiredException;

/**
 * Small utility for normalising the free-form {@code X-Operator-Reason} value
 * stamped on every {@code admin_actions} row. Centralised here (TASK-BE-288)
 * when the TASK-BE-121 use-case helper was folded into
 * {@link com.example.admin.application.port.AdminOperatorPort}.
 *
 * <p>Behaviour is identical to the original TASK-BE-121 {@code normalizeReason}
 * helper: null / blank → {@link #NOT_PROVIDED}, otherwise the value unchanged.
 */
public final class AuditReasons {

    /** Sentinel inserted into the audit row when the operator omitted the reason. */
    public static final String NOT_PROVIDED = "<not_provided>";

    private AuditReasons() {
        // utility — no instances
    }

    public static String normalize(String reason) {
        return (reason == null || reason.isBlank()) ? NOT_PROVIDED : reason;
    }

    /**
     * Validates that a mandatory operator reason is present, throwing
     * {@link ReasonRequiredException} when null or blank. Shared by the lock/unlock,
     * GDPR, and session command use cases (which previously each declared a private
     * {@code requireReason} copy).
     */
    public static void require(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new ReasonRequiredException();
        }
    }
}
