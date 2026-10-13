package com.example.admin.application;

import com.example.admin.application.exception.OperatorUnauthorizedException;
import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.application.port.AdminOperatorTotpPort;
import com.example.admin.application.port.OperatorTenantAssignmentPort;
import com.example.admin.domain.rbac.AdminOperator;
import com.example.admin.domain.rbac.Permission;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

@Service
@RequiredArgsConstructor
public class OperatorQueryService {

    private final AdminOperatorPort operatorPort;
    private final AdminOperatorTotpPort totpPort;
    private final OperatorTenantAssignmentPort assignmentPort;
    private final QueryTenantScopeGate queryTenantScopeGate;

    /** Pre-TASK-MONO-784 S3 call shape — no acting tenant (every role row's name). */
    @Transactional(readOnly = true)
    public OperatorSummary getCurrentOperator(String operatorUuid) {
        return getCurrentOperator(operatorUuid, null);
    }

    /**
     * {@code GET /api/admin/me}. TASK-MONO-784 S3 (admin-api.md § GET /api/admin/me · decision D-16): no
     * authorization decision — two response fields follow the operator's companies:
     * <ul>
     *   <li>{@code roles} — with an acting tenant T ({@code X-Tenant-Id}): the names of the role rows that
     *       live in T plus the {@code '*'} rows; without one, every role row's name (unchanged);</li>
     *   <li>{@code memberships} — one entry per membership ({@code operator_tenant_assignment} row), tenant
     *       ascending, each with the role names that live there (empty allowed — rider R2). {@code []} for a
     *       {@code '*'} operator ({@code '*'} is not a membership, ADR-082 D2).</li>
     * </ul>
     */
    @Transactional(readOnly = true)
    public OperatorSummary getCurrentOperator(String operatorUuid, String actingTenantId) {
        AdminOperatorPort.OperatorView operator = operatorPort.findByOperatorId(operatorUuid)
                .orElseThrow(() -> new OperatorUnauthorizedException(
                        "Operator not found for operatorId=" + operatorUuid));
        Map<String, List<String>> rolesByTenant = operatorPort.findRoleNamesByTenant(operator.internalId());

        boolean scoped = actingTenantId != null && !actingTenantId.isBlank();
        Set<String> names = new TreeSet<>();
        for (Map.Entry<String, List<String>> e : rolesByTenant.entrySet()) {
            if (!scoped || actingTenantId.equals(e.getKey())
                    || AdminOperator.PLATFORM_TENANT_ID.equals(e.getKey())) {
                names.addAll(e.getValue());
            }
        }

        List<Membership> memberships = new ArrayList<>();
        if (!AdminOperator.PLATFORM_TENANT_ID.equals(operator.tenantId())) {
            for (String tenant : new TreeSet<>(assignmentPort.findAssignedTenantIds(operator.internalId()))) {
                memberships.add(new Membership(tenant, rolesByTenant.getOrDefault(tenant, List.of())));
            }
        }
        return toSummary(operator, new ArrayList<>(names), List.copyOf(memberships));
    }

    /**
     * TASK-MONO-175 / ADR-MONO-020 — tenant-scoped operator listing: the operators with a membership
     * ({@code operator_tenant_assignment} row) in the acting tenant (784 S2). A platform-scope caller
     * listing {@code '*'} gets the unscoped cross-tenant view.
     *
     * <p><b>TASK-MONO-784 S3</b> (admin-api.md § 행동 테넌트 #6 · AC-1): the acting tenant is judged by the
     * one gate, {@link QueryTenantScopeGate}, against the {@code operator.manage} GRANT scope — a company
     * where the caller is only assigned is refused (before S3 this class carried its own copy of the
     * effective-scope rule, home ∪ assignments). An omitted {@code tenantId} follows the omitted-tenant rule.
     * Refusal writes no audit row (read — Side Effects: none). Each item's {@code roles} are the role rows
     * that live in the listed tenant only ({@code '*'} listing: every row) — another company's roles are
     * not shown to this company's administrator.
     *
     * @param callerOperatorId  the authenticated operator's external UUID (JWT {@code sub})
     * @param requestedTenantId the acting tenant; null/blank → the omitted-tenant rule
     */
    @Transactional(readOnly = true)
    public OperatorPage listOperators(String statusFilter, int page, int size,
                                      String callerOperatorId, String requestedTenantId) {
        QueryTenantScopeGate.Resolved resolved = queryTenantScopeGate.resolveWithoutDenyRow(
                new OperatorContext(callerOperatorId, null), requestedTenantId, Permission.OPERATOR_MANAGE);
        String tenantId = resolved.tenantId();
        boolean allTenants = resolved.isPlatformScope() && AdminOperator.PLATFORM_TENANT_ID.equals(tenantId);

        AdminOperatorPort.OperatorPage rows = allTenants
                ? operatorPort.findOperatorsPage(statusFilter, page, size)
                : operatorPort.findOperatorsPageByTenant(tenantId, statusFilter, page, size);

        List<AdminOperatorPort.OperatorView> content = rows.content();
        List<Long> operatorIds = new ArrayList<>(content.size());
        for (AdminOperatorPort.OperatorView op : content) operatorIds.add(op.internalId());

        Map<Long, List<String>> rolesByOperator = allTenants
                ? operatorPort.bulkLoadRoleNamesByOperator(operatorIds)
                : operatorPort.bulkLoadRoleNamesByOperatorInTenant(operatorIds, tenantId);
        Set<Long> enrolledIds = totpPort.findEnrolledOperatorIds(operatorIds);

        List<OperatorSummary> summaries = new ArrayList<>(content.size());
        for (AdminOperatorPort.OperatorView op : content) {
            List<String> roles = rolesByOperator.getOrDefault(op.internalId(), List.of());
            summaries.add(new OperatorSummary(
                    op.operatorId(),
                    op.email(),
                    op.displayName(),
                    op.status(),
                    roles,
                    op.totpEnrolledAt() != null || enrolledIds.contains(op.internalId()),
                    op.lastLoginAt(),
                    op.createdAt(),
                    op.financeDefaultAccountId(),
                    op.tenantId(),
                    null)); // memberships: /me only — never on a company's list (admin-api.md § GET /me)
        }
        return new OperatorPage(summaries, rows.totalElements(), rows.page(),
                rows.size(), rows.totalPages());
    }

    private OperatorSummary toSummary(AdminOperatorPort.OperatorView operator, List<String> roles,
                                      List<Membership> memberships) {
        boolean totpEnrolled = operator.totpEnrolledAt() != null
                || totpPort.findByOperator(operator.internalId())
                        .map(row -> row != null && row.enrolledAt() != null).orElse(false);
        return new OperatorSummary(
                operator.operatorId(),
                operator.email(),
                operator.displayName(),
                operator.status(),
                roles,
                totpEnrolled,
                operator.lastLoginAt(),
                operator.createdAt(),
                operator.financeDefaultAccountId(),
                operator.tenantId(),
                memberships);
    }

    /** TASK-MONO-784 S3 — one membership on {@code GET /api/admin/me}: the tenant and the roles that live there. */
    public record Membership(String tenantId, List<String> roles) {}

    /**
     * TASK-BE-308 — {@code financeDefaultAccountId} carries the
     * {@code admin_operators.finance_default_account_id} column value so the
     * {@code GET /api/admin/operators} list response can expose each operator's
     * current {@code operatorContext.defaultAccountId}. {@code null} when the
     * column is NULL. The controller maps {@code null} to absent
     * {@code operatorContext} via field-level
     * {@code @JsonInclude(Include.NON_NULL)} — the wire shape's
     * {@code operatorContext} key is omitted, never literal null.
     *
     * <p>TASK-BE-626 — {@code homeTenantId} is the operator's HOME tenant
     * ({@code admin_operators.tenant_id}, {@code '*'} for platform operators). The
     * tenant-scoped list returns HOME ∪ ASSIGNED operators, so a consumer needs this
     * to tell them apart (e.g. the console's operator-group member picker — group
     * membership is HOME-only, {@code GroupAdminUseCase.addMember}).
     */
    public record OperatorSummary(
            String operatorId,
            String email,
            String displayName,
            String status,
            List<String> roles,
            boolean totpEnrolled,
            Instant lastLoginAt,
            Instant createdAt,
            String financeDefaultAccountId,
            String homeTenantId,
            /* TASK-MONO-784 S3 — /me only; null on list items (omitted on the wire). */
            List<Membership> memberships
    ) {
        /** Pre-TASK-MONO-784 S3 shape — no memberships (list items). */
        public OperatorSummary(String operatorId, String email, String displayName, String status,
                               List<String> roles, boolean totpEnrolled, Instant lastLoginAt,
                               Instant createdAt, String financeDefaultAccountId, String homeTenantId) {
            this(operatorId, email, displayName, status, roles, totpEnrolled, lastLoginAt, createdAt,
                    financeDefaultAccountId, homeTenantId, null);
        }
    }

    public record OperatorPage(
            List<OperatorSummary> content,
            long totalElements,
            int page,
            int size,
            int totalPages
    ) {}
}
