package com.example.admin.application;

import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.application.port.OperatorTenantAssignmentPort;
import com.example.admin.domain.rbac.AdminOperator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * TASK-MONO-784 S2 (ADR-MONO-082 b · D3 · decision D-10; auth-to-admin.md § console-eligibility) — the ONE
 * predicate «is this operator row a live operator that may enter the console»:
 *
 * <pre>
 *   status = ACTIVE  ∧  ( tenant_id = '*'  ∨  memberships (operator_tenant_assignment rows) ≥ 1 )
 * </pre>
 *
 * <p>Used by the operator token exchange ({@link TokenExchangeService}). Until TASK-MONO-773 S5 it was shared with
 * the console-eligibility read ({@code GET /internal/operators/console-eligibility}, 772 D-5 «the same predicate
 * as the exchange») that auth-service asked before minting a console token; S5 removed that read — auth-service
 * now mints the console «non-operator shell» token for every pool account (773 OD-6) and the exchange alone
 * decides operator-ness ({@code 401} = not an operator).
 *
 * <p>Why membership: «export from this company» (S4) may remove an operator's last membership while the row
 * stays {@code ACTIVE} (D-10 — re-invitation adds a membership to the same row; the auditor needs the row).
 * Without this clause such a row would enter an empty console (784 AC-0 F16, Edge Case 2). The home tenant is
 * not a membership (R1). A {@code '*'} operator has no memberships by definition (ADR-082 D2) and is admitted
 * as before.
 */
@Component
@RequiredArgsConstructor
public class OperatorConsoleAdmission {

    /** {@code admin_operators.status} the console admits. */
    static final String ACTIVE = "ACTIVE";

    private final OperatorTenantAssignmentPort assignmentPort;

    /** {@code status = ACTIVE ∧ ('*' ∨ membership ≥ 1)}. */
    public boolean admits(AdminOperatorPort.OperatorView operator) {
        return ACTIVE.equals(operator.status()) && hasMembershipOrIsPlatform(operator);
    }

    /**
     * The membership half alone — for callers that already answered the status half with their own
     * failure message (the token exchange). {@code true} for a {@code '*'} operator without reading
     * assignments.
     */
    public boolean hasMembershipOrIsPlatform(AdminOperatorPort.OperatorView operator) {
        if (AdminOperator.PLATFORM_TENANT_ID.equals(operator.tenantId())) {
            return true;
        }
        return !assignmentPort.findAssignedTenantIds(operator.internalId()).isEmpty();
    }
}
