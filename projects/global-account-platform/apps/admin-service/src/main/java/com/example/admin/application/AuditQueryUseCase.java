package com.example.admin.application;

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
 */
@Service
@RequiredArgsConstructor
public class AuditQueryUseCase {

    private final AdminActionJpaRepository adminActionRepo;
    private final SecurityServiceClient securityServiceClient;
    private final AdminActionAuditor auditor;

    public AuditQueryResult query(QueryAuditCommand cmd) {
        // Authorization is enforced by RequiresPermissionAspect on the
        // controller (base audit.read + conditional security.event.read for
        // security-event sources). This use-case is invoked only after grant.

        int size = Math.min(Math.max(cmd.size(), 1), 100);
        int page = Math.max(cmd.page(), 0);

        String source = cmd.source();
        boolean includeAdmin = source == null || "admin".equals(source);
        boolean includeLogin = source == null || "login_history".equals(source);
        boolean includeSuspicious = source == null || "suspicious".equals(source);

        List<AuditQueryResult.Entry> entries = new ArrayList<>();
        long totalElements = 0;

        if (includeAdmin) {
            Page<AdminActionJpaEntity> adminPage = adminActionRepo.search(
                    cmd.accountId(),
                    cmd.actionCode(),
                    cmd.from(),
                    cmd.to(),
                    PageRequest.of(page, size));
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
                now));

        int totalPages = (int) Math.ceil((double) totalElements / (double) size);
        return new AuditQueryResult(entries, page, size, totalElements, Math.max(totalPages, 1));
    }
}
