package com.example.erp.approval.application.port.outbound;

/**
 * Outcome of a masterdata employee lookup (TASK-MONO-776 — approval-api.md § v2.4). Three
 * outcomes, never folded into one: an answer that the employee exists, an answer that it does
 * not ({@code 404} — not a failure), and «we could not ask» (401/403/5xx/timeout/no
 * credential). The third is counted by the adapter under its own {@code cause}; the use case
 * still refuses the operation, but an operator can tell an infrastructure problem from a
 * customer-data one (the TASK-ERP-BE-041 lesson, now for people as well as subjects).
 *
 * @param outcome   which of the three happened
 * @param id        the employee id (FOUND only)
 * @param status    {@code ACTIVE} / {@code RETIRED} (FOUND only)
 * @param accountId the linked IAM account, {@code null} when unlinked (FOUND only)
 */
public record EmployeeLookup(Outcome outcome, String id, String status, String accountId) {

    public enum Outcome { FOUND, NOT_FOUND, UNAVAILABLE }

    private static final String STATUS_ACTIVE = "ACTIVE";

    public static EmployeeLookup found(String id, String status, String accountId) {
        return new EmployeeLookup(Outcome.FOUND, id, status, accountId);
    }

    public static EmployeeLookup notFound() {
        return new EmployeeLookup(Outcome.NOT_FOUND, null, null, null);
    }

    public static EmployeeLookup unavailable() {
        return new EmployeeLookup(Outcome.UNAVAILABLE, null, null, null);
    }

    public boolean isFound() {
        return outcome == Outcome.FOUND;
    }

    public boolean isUnavailable() {
        return outcome == Outcome.UNAVAILABLE;
    }

    /** FOUND and {@code ACTIVE}. */
    public boolean isActive() {
        return isFound() && STATUS_ACTIVE.equalsIgnoreCase(status);
    }

    /** FOUND with a linked IAM account. */
    public boolean isLinked() {
        return isFound() && accountId != null && !accountId.isBlank();
    }
}
