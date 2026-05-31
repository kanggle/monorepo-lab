package com.example.admin.application;

import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.domain.rbac.AdminOperator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * TASK-BE-327 / ADR-MONO-020 § 3.3 step 2 (D2) — resolves whether an operator
 * (identified by GAP OIDC subject) has the SELECTED customer tenant within their
 * effective tenant scope. Read-only authorization helper that backs the
 * auth-service assume-tenant exchange's fail-closed assignment gate
 * ({@code GET /internal/operator-assignments/check}).
 *
 * <p><b>Fail-closed read</b> (mirrors {@link TokenExchangeService} resolution
 * order): missing {@code admin_operators} row OR a non-{@code ACTIVE} operator →
 * {@code assigned=false}; never leaks operator existence vs unassigned beyond the
 * boolean.
 *
 * <p><b>Platform-scope sentinel</b> ({@code tenant_id == '*'}): a platform-scope
 * operator is assigned to any non-blank tenant ({@link AdminOperator#isPlatformScope()}
 * grants all tenants). The assumed token still carries the concrete selected
 * {@code tenant_id} — never the {@code '*'} sentinel (that resolution happens in
 * auth-service).
 *
 * <p>Otherwise the decision is delegated to {@link TenantScopeResolver} (BE-326
 * dual-read: assignment rows ∪ {legacy home tenant}).
 *
 * <p><b>No {@code admin_actions} row</b> — read-only (ADR-014 token-exchange
 * "not audited" rule; the subsequent domain commands each audit). No
 * {@code @Transactional}: a fail-closed read only.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperatorAssignmentCheckUseCase {

    private final AdminOperatorPort operatorPort;
    private final TenantScopeResolver tenantScopeResolver;

    /**
     * @param oidcSubject the operator's GAP OIDC {@code sub} (account_id)
     * @param tenantId    the selected (assume-target) customer tenant id
     * @return {@code true} iff the operator's effective tenant scope contains
     *         {@code tenantId}; {@code false} fail-closed otherwise
     */
    public boolean isAssigned(String oidcSubject, String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            // Blank/malformed selected tenant never resolves to an assignment.
            return false;
        }

        // 1. Resolve oidc_subject → admin_operators row, FAIL-CLOSED.
        AdminOperatorPort.OperatorView operator = operatorPort.findByOidcSubject(oidcSubject).orElse(null);
        if (operator == null) {
            log.debug("assignment-check fail-closed: no admin_operators row for the OIDC subject");
            return false;
        }
        if (!"ACTIVE".equals(operator.status())) {
            log.debug("assignment-check fail-closed: operator status={} (not ACTIVE)", operator.status());
            return false;
        }

        // 2. Platform-scope sentinel → assigned to any non-blank tenant.
        if (AdminOperator.PLATFORM_TENANT_ID.equals(operator.tenantId())) {
            return true;
        }

        // 3. Dual-read effective scope: assignment rows ∪ {legacy home tenant}.
        Set<String> effectiveScope = tenantScopeResolver.resolveEffectiveTenantScope(
                operator.internalId(), operator.tenantId());
        return effectiveScope.contains(tenantId);
    }
}
