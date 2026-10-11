package com.example.admin.application;

import com.example.admin.application.exception.SecondFactorRequirementUnavailableException;
import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.application.port.OperatorTenantAssignmentPort;
import com.example.admin.application.port.TenantEntryPolicyPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * TASK-MONO-771 S4 (ADR-MONO-080 D4 · R2, owner decisions OD-2 · OD-3) — whether an operator's entry
 * requires a second factor. The ONE place both entries compute the requirement, so the two halves cannot
 * drift (security.md § Second-Factor Requirement):
 *
 * <ul>
 *   <li><b>Operator token exchange</b> ({@link #requiredForTokenExchange}) — ① the operator holds a role
 *       with {@code require_2fa = TRUE} (OD-2: the flag the break-glass login always read is now read on
 *       the primary path too) <b>or</b> ② any of the operator's <b>memberships</b>
 *       ({@code operator_tenant_assignment} rows) has its entry policy on (OD-3). <b>TASK-MONO-784 S2
 *       (ADR-MONO-082 b · R1 · AC-0 F17):</b> the home {@code admin_operators.tenant_id} is no longer added
 *       — it is not a membership; V0051 backfilled a home assignment row for every pre-784 operator, so a
 *       single-company operator's answer is unchanged. Partnerships are NOT memberships (ADR-MONO-045 —
 *       they never widen the admin scope), so they are not read here.</li>
 *   <li><b>Assume-tenant</b> ({@link #requiredForAssume}) — the selected tenant's entry policy ∨ ①
 *       (auth-to-admin.md rule 6). Path-independent: the policy belongs to the tenant being entered.</li>
 * </ul>
 *
 * <p><b>Fail-closed reads.</b> Every read is local (admin_db). Any failure becomes
 * {@link SecondFactorRequirementUnavailableException} (→ 500) — never «not required», never 401/403.
 *
 * <p>The comparison with the subject's {@code amr} ({@code "mfa" ∈ amr}) is NOT here: the token exchange
 * does it in {@link TokenExchangeService}; for assume-tenant it is auth-service (the issuer) that compares.
 */
@Service
@RequiredArgsConstructor
public class OperatorSecondFactorRequirement {

    private final AdminOperatorPort operatorPort;
    private final OperatorTenantAssignmentPort assignmentPort;
    private final TenantEntryPolicyPort entryPolicyPort;

    /** Token exchange: role flag ∨ any policy-ON tenant among the memberships (partnerships excluded). */
    public boolean requiredForTokenExchange(AdminOperatorPort.OperatorView operator) {
        try {
            if (operatorPort.anyRoleRequires2fa(operator.internalId())) {
                return true;
            }
            // TASK-MONO-784 S2: memberships only — the home is not added (R1, F17).
            Set<String> memberships = new LinkedHashSet<>(
                    assignmentPort.findAssignedTenantIds(operator.internalId()));
            return !entryPolicyPort.findTenantsRequiringMfa(memberships).isEmpty();
        } catch (RuntimeException e) {
            throw new SecondFactorRequirementUnavailableException(e);
        }
    }

    /** Assume-tenant: the selected tenant's entry policy ∨ role flag. Called only after {@code assigned=true}. */
    public boolean requiredForAssume(AdminOperatorPort.OperatorView operator, String tenantId) {
        try {
            if (!entryPolicyPort.findTenantsRequiringMfa(List.of(tenantId)).isEmpty()) {
                return true;
            }
            return operatorPort.anyRoleRequires2fa(operator.internalId());
        } catch (RuntimeException e) {
            throw new SecondFactorRequirementUnavailableException(e);
        }
    }
}
