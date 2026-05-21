package com.example.admin.application;

import com.example.admin.application.exception.OperatorUnauthorizedException;
import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.application.port.AdminOperatorTotpPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OperatorQueryService {

    private final AdminOperatorPort operatorPort;
    private final AdminOperatorTotpPort totpPort;

    @Transactional(readOnly = true)
    public OperatorSummary getCurrentOperator(String operatorUuid) {
        AdminOperatorPort.OperatorView operator = operatorPort.findByOperatorId(operatorUuid)
                .orElseThrow(() -> new OperatorUnauthorizedException(
                        "Operator not found for operatorId=" + operatorUuid));
        List<String> roleNames = new ArrayList<>();
        for (AdminOperatorPort.RoleView r : operatorPort.findRolesForOperator(operator.internalId())) {
            roleNames.add(r.name());
        }
        Collections.sort(roleNames);
        return toSummary(operator, roleNames);
    }

    @Transactional(readOnly = true)
    public OperatorPage listOperators(String statusFilter, int page, int size) {
        AdminOperatorPort.OperatorPage rows = operatorPort.findOperatorsPage(statusFilter, page, size);

        List<AdminOperatorPort.OperatorView> content = rows.content();
        List<Long> operatorIds = new ArrayList<>(content.size());
        for (AdminOperatorPort.OperatorView op : content) operatorIds.add(op.internalId());

        Map<Long, List<String>> rolesByOperator = operatorPort.bulkLoadRoleNamesByOperator(operatorIds);
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
                    op.financeDefaultAccountId()));
        }
        return new OperatorPage(summaries, rows.totalElements(), rows.page(),
                rows.size(), rows.totalPages());
    }

    private OperatorSummary toSummary(AdminOperatorPort.OperatorView operator, List<String> roles) {
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
                operator.financeDefaultAccountId());
    }

    /**
     * TASK-BE-308 — {@code financeDefaultAccountId} carries the
     * {@code admin_operators.finance_default_account_id} column value so the
     * {@code GET /api/admin/operators} list response can expose each operator's
     * current {@code operatorContext.defaultAccountId}. {@code null} when the
     * column is NULL. The controller maps {@code null} to absent
     * {@code operatorContext} via field-level
     * {@code @JsonInclude(Include.NON_NULL)} — the wire shape's
     * {@code operatorContext} key is omitted, never literal null.
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
            String financeDefaultAccountId
    ) {}

    public record OperatorPage(
            List<OperatorSummary> content,
            long totalElements,
            int page,
            int size,
            int totalPages
    ) {}
}
