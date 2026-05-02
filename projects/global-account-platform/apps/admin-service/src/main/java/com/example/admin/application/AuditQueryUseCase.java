package com.example.admin.application;

import com.example.admin.application.exception.TenantScopeDeniedException;
import com.example.admin.application.port.OperatorLookupPort;
import com.example.admin.domain.rbac.AdminOperator;
import com.example.admin.infrastructure.client.SecurityServiceClient;
import com.example.admin.infrastructure.persistence.AdminActionJpaEntity;
import com.example.admin.infrastructure.persistence.AdminActionJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * GET /api/admin/audit — integrated audit view across:
 *   - admin_actions (local)
 *   - login_history (security-service)
 *   - suspicious_events (security-service)
 *
 * Meta-audit: the act of querying is itself recorded as AUDIT_QUERY in admin_actions.
 *
 * <p>TASK-BE-249: tenant-scope enforcement added.
 * <ul>
 *   <li>Normal operators: can only query their own {@code tenantId}.</li>
 *   <li>SUPER_ADMIN (tenantId='*'): may query any tenant via {@code tenantId=*}
 *       (returns cross-tenant rows) or a specific {@code tenantId}.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class AuditQueryUseCase {

    private final AdminActionJpaRepository adminActionRepo;
    private final SecurityServiceClient securityServiceClient;
    private final AdminActionAuditor auditor;
    private final OperatorLookupPort operatorLookupPort;

    public AuditQueryResult query(QueryAuditCommand cmd) {
        // Authorization is enforced by RequiresPermissionAspect on the
        // controller (base audit.read + conditional security.event.read for
        // security-event sources). This use-case is invoked only after grant.

        // --- TASK-BE-249: resolve operator tenantId then enforce scope ---
        OperatorLookupPort.OperatorSummary opSummary = operatorLookupPort
                .findByOperatorId(cmd.operator().operatorId())
                .orElseThrow(() -> new TenantScopeDeniedException(
                        "Operator not found: " + cmd.operator().operatorId()));

        String operatorTenantId = opSummary.tenantId();
        boolean isPlatformScope = AdminOperator.PLATFORM_TENANT_ID.equals(operatorTenantId);

        // Resolve effective tenantId for the query
        String requestedTenantId = cmd.tenantId();
        if (requestedTenantId == null || requestedTenantId.isBlank()) {
            // Default: operator's own tenant
            requestedTenantId = operatorTenantId;
        }

        // Tenant-scope gate: non-platform-scope operators may only query their own tenant.
        // TASK-BE-262: record a best-effort DENIED audit row before throwing.
        if (!isPlatformScope && !operatorTenantId.equals(requestedTenantId)) {
            auditor.recordCrossTenantDenied(
                    cmd.operator(),
                    operatorTenantId,
                    ActionCode.AUDIT_QUERY,
                    "audit.read",
                    requestedTenantId);
            throw new TenantScopeDeniedException(
                    "Operator tenantId=" + operatorTenantId
                            + " cannot query tenantId=" + requestedTenantId);
        }

        int size = Math.min(Math.max(cmd.size(), 1), 100);
        int page = Math.max(cmd.page(), 0);

        String source = cmd.source();
        boolean includeAdmin = source == null || "admin".equals(source);
        boolean includeLogin = source == null || "login_history".equals(source);
        boolean includeSuspicious = source == null || "suspicious".equals(source);

        List<AuditQueryResult.Entry> entries = new ArrayList<>();
        long totalElements = 0;

        if (includeAdmin) {
            Page<AdminActionJpaEntity> adminPage;
            if (isPlatformScope && AdminOperator.PLATFORM_TENANT_ID.equals(requestedTenantId)) {
                // SUPER_ADMIN querying tenantId='*' → return cross-tenant platform rows
                adminPage = adminActionRepo.findByTenantId(
                        AdminOperator.PLATFORM_TENANT_ID,
                        cmd.accountId(), cmd.actionCode(),
                        cmd.from(), cmd.to(),
                        PageRequest.of(page, size));
            } else if (isPlatformScope) {
                // SUPER_ADMIN querying a specific tenant → use cross-tenant finder
                adminPage = adminActionRepo.searchCrossTenant(
                        requestedTenantId,
                        cmd.accountId(), cmd.actionCode(),
                        cmd.from(), cmd.to(),
                        PageRequest.of(page, size));
            } else {
                // Normal operator — own tenant only
                adminPage = adminActionRepo.findByTenantId(
                        operatorTenantId,
                        cmd.accountId(), cmd.actionCode(),
                        cmd.from(), cmd.to(),
                        PageRequest.of(page, size));
            }
            totalElements += adminPage.getTotalElements();
            for (AdminActionJpaEntity e : adminPage.getContent()) {
                entries.add(new AuditQueryResult.Entry(
                        "admin",
                        e.getLegacyAuditId(),
                        null,
                        e.getActionCode(),
                        e.getActorId(),
                        null,
                        e.getTargetId(),
                        e.getReason(),
                        e.getOutcome(),
                        null,
                        null,
                        e.getStartedAt()));
            }
        }

        if (includeLogin && cmd.accountId() != null) {
            List<SecurityServiceClient.LoginHistoryEntry> lh =
                    securityServiceClient.queryLoginHistory(cmd.accountId(), cmd.from(), cmd.to());
            totalElements += lh.size();
            for (SecurityServiceClient.LoginHistoryEntry e : lh) {
                entries.add(new AuditQueryResult.Entry(
                        "login_history",
                        null,
                        e.eventId(),
                        null,
                        null,
                        e.accountId(),
                        e.accountId(),
                        null,
                        e.outcome(),
                        e.ipMasked(),
                        e.geoCountry(),
                        e.occurredAt()));
            }
        }

        if (includeSuspicious && cmd.accountId() != null) {
            List<SecurityServiceClient.SuspiciousEventEntry> se =
                    securityServiceClient.querySuspiciousEvents(cmd.accountId(), cmd.from(), cmd.to());
            totalElements += se.size();
            for (SecurityServiceClient.SuspiciousEventEntry e : se) {
                entries.add(new AuditQueryResult.Entry(
                        "suspicious",
                        null,
                        e.eventId(),
                        e.signalType(),
                        null,
                        e.accountId(),
                        e.accountId(),
                        null,
                        null,
                        e.ipMasked(),
                        null,
                        e.occurredAt()));
            }
        }

        entries.sort(Comparator.comparing(AuditQueryResult.Entry::occurredAt,
                Comparator.nullsLast(Comparator.reverseOrder())));

        // meta-audit: record the audit query itself
        final String effectiveTenantId = requestedTenantId;
        String auditId = auditor.reserveAuditId();
        Instant now = Instant.now();
        auditor.record(new AdminActionAuditor.AuditRecord(
                auditId,
                ActionCode.AUDIT_QUERY,
                cmd.operator(),
                "audit",
                cmd.accountId() != null ? cmd.accountId() : "*",
                cmd.reason() != null ? cmd.reason() : "audit-query",
                null,
                cmd.idempotencyKey() != null ? cmd.idempotencyKey() : UUID.randomUUID().toString(),
                Outcome.SUCCESS,
                null,
                now,
                now,
                effectiveTenantId));  // TASK-BE-249: targetTenantId for meta-audit row

        int totalPages = (int) Math.ceil((double) totalElements / (double) size);
        return new AuditQueryResult(entries, page, size, totalElements, Math.max(totalPages, 1));
    }
}
