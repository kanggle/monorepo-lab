package com.example.admin.application;

import com.example.admin.application.exception.TenantScopeDeniedException;
import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.domain.rbac.Permission;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * TASK-MONO-777 — {@code GET /api/admin/operators/lookup?email=} (admin-api.md § same name).
 *
 * <p>Finds the operators of the active tenant by exact e-mail and returns the account id each
 * carries into the console ({@code oidc_subject} = the console token {@code sub}) — the value an
 * erp employee ↔ account link needs. {@code GET /api/admin/accounts?email=} cannot answer this: it
 * searches {@code account_db} rows of the tenant, and operators whose console credential lives in
 * the {@code iam} tenant (or in the consumer pool, ADR-MONO-080) have none there.
 *
 * <p>Authorization (owner decision 2026-10-08 UTC, «L2»): <b>no permission key</b> — the same shape
 * as the accounts e-mail branch — and the SAME tenant gate, {@link QueryTenantScopeGate} (no new
 * evaluator). One difference, on purpose: out of scope is answered with the SAME empty list as
 * «not found», byte for byte, instead of {@code 403 TENANT_SCOPE_DENIED} — the response must not
 * tell which tenants are in scope. The gate still writes its best-effort DENIED row (rbac.md D3),
 * reusing {@link ActionCode#ACCOUNT_SEARCH}: a new action code would need a permission-registry
 * mapping, which the decision rules out. A successful read writes no audit row (BE-486).
 */
@Service
@RequiredArgsConstructor
public class OperatorEmailLookupUseCase {

    private final QueryTenantScopeGate queryTenantScopeGate;
    private final AdminOperatorPort operatorPort;

    @Transactional(readOnly = true)
    public List<AdminOperatorPort.OperatorLookupView> lookup(OperatorContext caller,
                                                             String requestedTenantId,
                                                             String email) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (normalizedEmail.isEmpty()) {
            throw new IllegalArgumentException("email is required");
        }
        String tenantId;
        try {
            tenantId = queryTenantScopeGate.resolve(
                    caller, requestedTenantId, ActionCode.ACCOUNT_SEARCH, Permission.ACCOUNT_READ)
                    .tenantId();
        } catch (TenantScopeDeniedException outOfScope) {
            // Existence non-disclosure: indistinguishable from «no such operator».
            return List.of();
        }
        return operatorPort.findLookupCandidates(tenantId, normalizedEmail);
    }
}
