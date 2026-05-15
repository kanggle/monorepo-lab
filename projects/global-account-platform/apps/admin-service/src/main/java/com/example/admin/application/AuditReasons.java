package com.example.admin.application;

/**
 * Small utility for normalising the free-form {@code X-Operator-Reason} value
 * stamped on every {@code admin_actions} row. Centralised here (TASK-BE-288)
 * after {@code OperatorRoleResolver} was folded into {@link com.example.admin.application.port.AdminOperatorPort}.
 *
 * <p>Behaviour is identical to the original {@code OperatorRoleResolver.normalizeReason}
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
}
