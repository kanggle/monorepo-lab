package com.example.admin.application;

import com.example.admin.application.exception.OperatorOwnedTenantLimitExceededException;
import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.domain.rbac.AdminOperator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * TASK-MONO-784 S5 (owner decision OD-1 · ADR-MONO-044 D4 · ADR-MONO-082 D4 — onboarding-api.md § 신원당 상한) —
 * the per-identity cap on SELF-SERVICE onboarding. 784 ships the counter; TASK-MONO-773 AC-0 sets the value.
 *
 * <ul>
 *   <li><b>Unit</b> (ADR-082 D4): the number of DISTINCT tenants of this operator row's {@code TENANT_ADMIN} role
 *       rows, {@code tenant_id <> '*'}, whatever their origin (self-service onboarding · invitation acceptance ·
 *       role PATCH · group fan-out · org-node row recorded there). Tenant status is not read (784 S1 § 4-2 left
 *       «count SUSPENDED tenants?» to 773 AC-0).</li>
 *   <li><b>Verdict</b>: {@code count ≥ cap} → refused — one more tenant would exceed the cap.</li>
 *   <li><b>Value</b>: {@code admin.onboarding.max-owned-tenants-per-operator}, default {@code 2} (exactly the
 *       «second company» 784 AC-3 needs).</li>
 *   <li>Only self-service onboarding asks (S1-5): the invitation acceptance does not — the cap guards tenants a
 *       person creates for themselves (ADR-044 D4), and an invitation is another company's decision.</li>
 * </ul>
 */
@Component
public class OperatorOwnedTenantLimit {

    static final String TENANT_ADMIN_ROLE = "TENANT_ADMIN";

    private final AdminOperatorPort operatorPort;
    private final int maxOwnedTenants;

    public OperatorOwnedTenantLimit(AdminOperatorPort operatorPort,
                                    @Value("${admin.onboarding.max-owned-tenants-per-operator:2}") int maxOwnedTenants) {
        if (maxOwnedTenants < 1) {
            throw new IllegalStateException(
                    "admin.onboarding.max-owned-tenants-per-operator must be at least 1: " + maxOwnedTenants);
        }
        this.operatorPort = operatorPort;
        this.maxOwnedTenants = maxOwnedTenants;
    }

    int maxOwnedTenants() {
        return maxOwnedTenants;
    }

    /** The distinct non-{@code '*'} tenants this row holds {@code TENANT_ADMIN} in. */
    Set<String> ownedTenants(long operatorInternalId) {
        return operatorPort.findRoleRowsOfOperator(operatorInternalId).stream()
                .filter(row -> TENANT_ADMIN_ROLE.equals(row.roleName()))
                .map(AdminOperatorPort.RoleRowView::tenantId)
                .filter(t -> t != null && !AdminOperator.PLATFORM_TENANT_ID.equals(t))
                .collect(Collectors.toSet());
    }

    /** @throws OperatorOwnedTenantLimitExceededException the row already administers {@code cap} tenants */
    public void requireBelowLimit(long operatorInternalId) {
        int owned = ownedTenants(operatorInternalId).size();
        if (owned >= maxOwnedTenants) {
            throw new OperatorOwnedTenantLimitExceededException(
                    "This operator already administers " + owned + " tenant(s); the per-identity limit is "
                            + maxOwnedTenants);
        }
    }
}
