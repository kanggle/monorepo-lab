package com.example.admin.application;

import com.example.admin.application.port.OperatorTenantAssignmentPort;
import com.example.admin.domain.rbac.AdminOperator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * TASK-BE-326 / ADR-MONO-020 D1 + D6 step 1 — the single dual-read core for an
 * operator's <em>effective tenant scope</em>.
 *
 * <p>Effective scope = assignment tenantIds ({@code operator_tenant_assignment})
 * ∪ {legacy home tenantId} ({@code admin_operators.tenant_id}). A platform-scope
 * operator ({@code tenant_id == '*'}) resolves to the unchanged sentinel
 * {@code {"*"}} — assignments are NOT expanded (the {@code '*'} sentinel is
 * field-level, assignment-independent, and {@link AdminOperator#isPlatformScope()}
 * stays byte-unchanged).
 *
 * <p><b>NET-ZERO:</b> with no assignment rows seeded, every operator resolves to
 * {@code {legacy tenantId}} — byte-identical to the pre-BE-326 single-value
 * behavior. Multi-assignment only takes effect once real assignment rows exist.
 *
 * <p>All four gating sites
 * ({@code ConsoleRegistryUseCase}, {@code PermissionEvaluator} via
 * {@code TenantAdminController}, {@code AuditQueryUseCase},
 * {@code UpdateOperatorProfileUseCase}) consume this resolver so the dual-read
 * is uniform.
 */
@Service
@RequiredArgsConstructor
public class TenantScopeResolver {

    private final OperatorTenantAssignmentPort assignmentPort;

    /**
     * Resolves the effective tenant scope.
     *
     * @param operatorInternalId internal BIGINT id ({@code admin_operators.id});
     *                           may be {@code null} (no assignment read performed)
     * @param legacyHomeTenantId the operator's home tenant
     *                           ({@code admin_operators.tenant_id})
     * @return immutable effective scope set. {@code {"*"}} when the home tenant is
     *         the platform sentinel; otherwise {@code assignments ∪ {legacyHomeTenantId}}.
     */
    public Set<String> resolveEffectiveTenantScope(Long operatorInternalId, String legacyHomeTenantId) {
        // Platform-scope sentinel: never expanded by assignments.
        if (AdminOperator.PLATFORM_TENANT_ID.equals(legacyHomeTenantId)) {
            return Set.of(AdminOperator.PLATFORM_TENANT_ID);
        }
        Set<String> effective = new LinkedHashSet<>();
        if (legacyHomeTenantId != null) {
            effective.add(legacyHomeTenantId); // legacy home tenant — net-zero base
        }
        effective.addAll(assignmentPort.findAssignedTenantIds(operatorInternalId));
        return Set.copyOf(effective);
    }
}
