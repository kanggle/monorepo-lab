package com.example.admin.application;

import com.example.admin.application.exception.SecondFactorRequirementUnavailableException;
import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.application.port.OperatorTenantAssignmentPort;
import com.example.admin.application.port.TenantEntryPolicyPort;
import com.example.admin.domain.rbac.AdminOperator;
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
 *       the primary path too) <b>or</b> ② any tenant of the operator's <b>admin scope</b> — home
 *       {@code admin_operators.tenant_id} ∪ {@code operator_tenant_assignment} rows — has its entry policy
 *       on (OD-3). Partnerships are NOT part of the admin scope (ADR-MONO-045 — they never widen it), so
 *       they are not read here. A {@code '*'} home contributes nothing ({@code '*'} cannot hold a policy);
 *       for such an operator ② reads the assignment rows only.</li>
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

    /** Token exchange: role flag ∨ any policy-ON tenant in home ∪ assignment rows (partnerships excluded). */
    public boolean requiredForTokenExchange(AdminOperatorPort.OperatorView operator) {
        try {
            if (operatorPort.anyRoleRequires2fa(operator.internalId())) {
                return true;
            }
            Set<String> adminScope = new LinkedHashSet<>();
            if (operator.tenantId() != null
                    && !AdminOperator.PLATFORM_TENANT_ID.equals(operator.tenantId())) {
                adminScope.add(operator.tenantId());
            }
            adminScope.addAll(assignmentPort.findAssignedTenantIds(operator.internalId()));
            return !entryPolicyPort.findTenantsRequiringMfa(adminScope).isEmpty();
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
