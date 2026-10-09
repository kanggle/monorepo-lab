package com.example.admin.application;

import com.example.admin.application.exception.OperatorNotFoundException;
import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.application.port.TenantEntryPolicyManagementPort;
import com.example.admin.application.port.TenantEntryPolicyManagementPort.EntryPolicyView;
import com.example.admin.application.port.TenantProvisioningPort;
import com.example.admin.domain.rbac.AdminOperator;
import com.example.admin.domain.rbac.Permission;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * TASK-MONO-771 S5 (ADR-MONO-080 D4 · R2, owner decisions OD-1 · OD-4) — the management surface of the
 * tenant entry policy: «entering this tenant AS AN OPERATOR requires a second factor»
 * (admin-api.md § Tenant Entry Policy). The policy itself is read by {@link OperatorSecondFactorRequirement}
 * at both entries (token exchange · assume-tenant); this class only reads and writes the row.
 *
 * <p><b>Who.</b> {@code tenant.security.manage} is the endpoint gate ({@code @RequiresPermission}). Holding
 * the key is not enough: the path {@code tenantId} must be in the caller's admin-grant scope for that key —
 * {@code SUPER_ADMIN} ({@code '*'}) reaches every tenant, a {@code TENANT_ADMIN} only its grant's tenant
 * ({@link TenantScopeGuard}, the one D2 decision site). Out of scope → 403 {@code TENANT_SCOPE_DENIED}; the
 * write path leaves a best-effort DENIED row, the read path none (BE-486 / MONO-737 read rule).
 *
 * <p><b>Order on write</b>, every time: validate the id (no {@code '*'}) → scope → tenant existence at the
 * authority (account-service; 404 / 503 and <i>nothing written</i> — an orphan row for a tenant that does not
 * exist is never created) → write → audit. The existence check runs only on writes; reads and the entry
 * decisions never call account-service (admin-api.md).
 *
 * <p><b>Transition (OD-4).</b> No grace period: the next exchange / assume after a write is judged. Turning
 * the policy on locks nobody out — an operator without a second factor is refused with a distinguished
 * answer that the console routes to the IAM enrolment screen (console-integration-contract § 2.6).
 */
@Service
@RequiredArgsConstructor
public class TenantEntryPolicyUseCase {

    /** Tenant-id shape (same as the partnership surface). {@code '*'} never matches. */
    private static final Pattern TENANT_ID_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]{0,31}$");

    private final TenantEntryPolicyManagementPort entryPolicyPort;
    private final TenantProvisioningPort provisioningPort;
    private final AdminOperatorPort operatorPort;
    private final TenantScopeGuard tenantScopeGuard;
    private final AdminActionAuditor auditor;

    /** {@code GET} — the row, or «off» when none. No audit row on success. */
    @Transactional(readOnly = true)
    public EntryPolicyView get(OperatorContext actor, String tenantId) {
        requireValidTenantId(tenantId);
        tenantScopeGuard.requireTenantReadable(actor, Permission.TENANT_SECURITY_MANAGE, tenantId);
        return entryPolicyPort.find(tenantId).orElseGet(() -> off(tenantId));
    }

    /**
     * {@code PUT} — full replace. Every successful call writes one audit row, a same-value no-op included.
     *
     * @param requireMfa the new value; {@code null} → 400 (the controller already rejects a missing field)
     */
    @Transactional
    public EntryPolicyView set(OperatorContext actor, String tenantId, Boolean requireMfa, String reason) {
        requireValidTenantId(tenantId);
        if (requireMfa == null) {
            throw new IllegalArgumentException("'requireMfa' is required (true or false)");
        }
        tenantScopeGuard.requireTenantInScope(
                actor, Permission.TENANT_SECURITY_MANAGE, tenantId, ActionCode.TENANT_ENTRY_POLICY_SET);

        // Existence at the authority BEFORE any write: TenantNotFoundException → 404,
        // DownstreamFailureException / circuit open → 503. Either way nothing below runs.
        provisioningPort.get(tenantId);

        AdminOperatorPort.OperatorView writer = operatorPort.findByOperatorId(actor.operatorId())
                .orElseThrow(() -> new OperatorNotFoundException(
                        "Operator not found for operatorId=" + actor.operatorId()));

        Optional<EntryPolicyView> before = entryPolicyPort.find(tenantId);
        Instant now = Instant.now();
        EntryPolicyView after = entryPolicyPort.save(tenantId, requireMfa, writer.internalId(), now);

        String auditId = auditor.newAuditId();
        auditor.recordWithPermission(new AdminActionAuditor.AuditRecord(
                auditId,
                ActionCode.TENANT_ENTRY_POLICY_SET,
                actor,
                "TENANT",
                tenantId,
                AuditReasons.normalize(reason),
                null,
                "tenant-entry-policy:" + auditId,
                Outcome.SUCCESS,
                "requireMfa " + before.map(b -> String.valueOf(b.requireMfa())).orElse("none")
                        + "→" + after.requireMfa(),
                now,
                Instant.now(),
                tenantId), Permission.TENANT_SECURITY_MANAGE);
        return after;
    }

    /** {@code '*'} cannot hold a policy (the platform scope's second factor is the role flag). */
    private static void requireValidTenantId(String tenantId) {
        if (tenantId == null || AdminOperator.PLATFORM_TENANT_ID.equals(tenantId)) {
            throw new IllegalArgumentException(
                    "tenantId '*' cannot hold an entry policy — the platform scope is governed by the role flag");
        }
        if (!TENANT_ID_PATTERN.matcher(tenantId).matches()) {
            throw new IllegalArgumentException("tenantId must match " + TENANT_ID_PATTERN.pattern());
        }
    }

    private static EntryPolicyView off(String tenantId) {
        return new EntryPolicyView(tenantId, false, null, null);
    }
}
