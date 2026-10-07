package com.example.erp.notification.application.port.outbound;

/**
 * «The employee linked to my {@code sub}» (notification-api.md § v1.1, TASK-MONO-776). The
 * recipient of every notification is an EMPLOYEE id (resolved from the approval event payload);
 * the caller is an IAM account. This port bridges the two via masterdata
 * {@code GET /api/erp/masterdata/employees/me}, propagating the caller's own token.
 *
 * <p>Three outcomes, never folded: linked (the employee id), not linked (an ANSWER — the inbox
 * is empty), could not ask (counted by the adapter; the use case answers 503 rather than show an
 * empty inbox that would read as «nothing for you»).
 */
public interface CallerEmployeePort {

    CallerEmployee resolve(String callerSub, String tenantId);

    record CallerEmployee(Outcome outcome, String employeeId) {

        public enum Outcome { LINKED, NOT_LINKED, UNAVAILABLE }

        public static CallerEmployee linked(String employeeId) {
            return new CallerEmployee(Outcome.LINKED, employeeId);
        }

        public static CallerEmployee notLinked() {
            return new CallerEmployee(Outcome.NOT_LINKED, null);
        }

        public static CallerEmployee unavailable() {
            return new CallerEmployee(Outcome.UNAVAILABLE, null);
        }
    }
}
