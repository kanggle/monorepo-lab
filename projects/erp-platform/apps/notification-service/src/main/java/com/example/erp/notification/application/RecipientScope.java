package com.example.erp.notification.application;

import com.example.erp.notification.application.port.outbound.CallerEmployeePort;
import com.example.erp.notification.application.port.outbound.CallerEmployeePort.CallerEmployee;
import com.example.erp.notification.domain.error.RecipientResolveUnavailableException;

import java.util.Optional;

/**
 * The recipient-scope predicate of every inbox read (notification-api.md § v1.1,
 * TASK-MONO-776): {@code recipient == the employee linked to caller.sub}. Before this, the
 * predicate was {@code recipient == caller.sub} — an IAM account UUID compared with an employee
 * id, which can never be equal once the approval side stores employee ids as its contract says.
 */
final class RecipientScope {

    private RecipientScope() {
    }

    /** The caller's employee, empty when unlinked; «could not ask» → 503. */
    static Optional<String> of(CallerEmployeePort port, String callerSub, String tenantId) {
        CallerEmployee me = port.resolve(callerSub, tenantId);
        return switch (me.outcome()) {
            case LINKED -> Optional.of(me.employeeId());
            case NOT_LINKED -> Optional.empty();
            case UNAVAILABLE -> throw new RecipientResolveUnavailableException();
        };
    }
}
