package com.example.admin.application;

import com.example.admin.application.exception.TenantScopeDeniedException;
import com.example.admin.application.port.OperatorLookupPort;
import com.example.admin.domain.rbac.AdminOperator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * TASK-BE-357 — the single dual-read tenant-scope gate for operator <em>read</em>
 * surfaces ({@code GET /api/admin/audit}, {@code GET /api/admin/accounts}).
 *
 * <p>Extracted verbatim from {@link AuditQueryUseCase} (TASK-BE-249/BE-326) so the
 * two read surfaces cannot drift: resolve the operator's effective tenant scope
 * (home ∪ assignments via {@link TenantScopeResolver}), default an omitted request
 * tenant to the operator's own, and enforce membership — a non-platform operator
 * requesting an out-of-scope tenant gets a best-effort DENIED {@code admin_actions}
 * row (TASK-BE-262) + {@code 403 TENANT_SCOPE_DENIED}.
 *
 * <p>This is the READ-surface gate (effective scope = home ∪ assignments). It is
 * distinct from {@link TenantScopeGuard}, which gates the <em>administration</em>
 * surface against the admin-<em>grant</em> scope (ADR-MONO-024). Do not conflate.
 *
 * <p><b>NET-ZERO:</b> with no assignment rows, {@link #resolve} reduces to the
 * legacy {@code operatorTenantId.equals(requested)} single-tenant behavior.
 */
@Component
@RequiredArgsConstructor
public class QueryTenantScopeGate {

    private final OperatorLookupPort operatorLookupPort;
    private final TenantScopeResolver tenantScopeResolver;
    private final AdminActionAuditor auditor;

    /**
     * The resolved query tenant plus whether the caller is platform-scope
     * (SUPER_ADMIN). Consumers branch on {@code isPlatformScope} when a cross-tenant
     * ({@code "*"}) finder differs from the single-tenant finder.
     */
    public record Resolved(String tenantId, boolean isPlatformScope) {}

    /**
     * Resolves + gates the query tenant.
     *
     * @param operator          the authenticated operator
     * @param requestedTenantId the requested tenant; {@code null}/blank → operator's own
     * @param actionCode        the audit action code for a DENIED row
     * @param permission        the permission cited on the DENIED row (informational)
     * @return the resolved tenant ({@code "*"} stays {@code "*"} for platform scope) + scope flag
     * @throws TenantScopeDeniedException operator not found, or requested tenant out of effective scope
     */
    public Resolved resolve(OperatorContext operator,
                            String requestedTenantId,
                            ActionCode actionCode,
                            String permission) {
        OperatorLookupPort.OperatorSummary opSummary = operatorLookupPort
                .findByOperatorId(operator.operatorId())
                .orElseThrow(() -> new TenantScopeDeniedException(
                        "Operator not found: " + operator.operatorId()));

        String operatorTenantId = opSummary.tenantId();
        boolean isPlatformScope = AdminOperator.PLATFORM_TENANT_ID.equals(operatorTenantId);

        // TASK-BE-326 dual-read effective scope (assignments ∪ home). NET-ZERO with
        // no assignments → {home tenant} → membership check == legacy equality.
        Set<String> effectiveTenants = tenantScopeResolver
                .resolveEffectiveTenantScope(opSummary.internalId(), operatorTenantId);

        String requested = (requestedTenantId == null || requestedTenantId.isBlank())
                ? operatorTenantId
                : requestedTenantId;

        if (!isPlatformScope && !effectiveTenants.contains(requested)) {
            // TASK-BE-262: best-effort DENIED audit row before throwing.
            auditor.recordCrossTenantDenied(operator, operatorTenantId, actionCode, permission, requested);
            throw new TenantScopeDeniedException(
                    "Operator tenantId=" + operatorTenantId
                            + " cannot query tenantId=" + requested);
        }

        return new Resolved(requested, isPlatformScope);
    }
}
