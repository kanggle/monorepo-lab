package com.example.admin.application;

import com.example.admin.application.port.SecondFactorEnrolmentPort;
import com.example.admin.domain.rbac.AdminOperator;
import com.example.admin.domain.rbac.Permission;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * TASK-MONO-771 S5 (owner decision OD-4 · ticket AC-0 § 4 «켜기 전 사전 점검») — before a tenant admin turns the
 * entry policy on, how many of the tenant's operators will be asked to enrol a second factor on their next entry
 * (admin-api.md § Tenant Entry Policy › {@code GET .../entry-policy/enrolment-summary}).
 *
 * <p>Advisory and read-only: no audit row, no write, never on an entry path. Same gate and scope as the policy
 * read ({@code tenant.security.manage}, path tenant in the caller's admin scope — 403 without a DENIED row).
 *
 * <p>Population = the tenant's ACTIVE operators (home ∪ assignment rows — the operator-list membership).
 * Counted three ways: {@code enrolled} / {@code notEnrolled} (account-linked, confirmed second factor or not) /
 * {@code unlinked} (no IAM account link — the token exchange cannot resolve them at all, so the policy does not
 * change anything for them). Platform ({@code '*'}) operators and partnership participants are not members of the
 * tenant and are not counted. auth-service unavailable → the failure propagates (503): the console then confirms
 * without a number rather than with a wrong one.
 */
@Service
@RequiredArgsConstructor
public class TenantEntryPolicyPrecheckUseCase {

    private static final Pattern TENANT_ID_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]{0,31}$");

    private final SecondFactorEnrolmentPort enrolmentPort;
    private final TenantScopeGuard tenantScopeGuard;

    public EnrolmentSummary summarize(OperatorContext actor, String tenantId) {
        if (tenantId == null || AdminOperator.PLATFORM_TENANT_ID.equals(tenantId)
                || !TENANT_ID_PATTERN.matcher(tenantId).matches()) {
            throw new IllegalArgumentException("tenantId must be a concrete tenant id ('*' cannot hold an entry policy)");
        }
        tenantScopeGuard.requireTenantReadable(actor, Permission.TENANT_SECURITY_MANAGE, tenantId);

        List<String> roster = enrolmentPort.activeOperatorAccountIdsOf(tenantId);
        List<String> linked = new ArrayList<>();
        int unlinked = 0;
        for (String accountId : roster) {
            if (accountId == null || accountId.isBlank()) {
                unlinked++;
            } else {
                linked.add(accountId);
            }
        }
        Set<String> enrolledIds = linked.isEmpty() ? Set.of() : enrolmentPort.enrolledAmong(linked);
        int enrolled = 0;
        for (String accountId : linked) {
            if (enrolledIds.contains(accountId)) enrolled++;
        }
        return new EnrolmentSummary(tenantId, roster.size(), enrolled, linked.size() - enrolled, unlinked);
    }

    /** {@code operators = enrolled + notEnrolled + unlinked}. */
    public record EnrolmentSummary(String tenantId, int operators, int enrolled, int notEnrolled, int unlinked) {}
}
