package com.example.admin.application;

import com.example.admin.application.exception.TenantLastAdminRequiredException;
import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.domain.rbac.AdminOperator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * TASK-MONO-784 S4 — «a company keeps an administrator» (owner decision 2026-10-11 UTC «거절 (409)»;
 * admin-api.md § DELETE …/assignments/{tenantId} «마지막 관리자 보호»).
 *
 * <p>Before 784 removing an assignment never removed a role, so a company could not lose its last
 * {@code TENANT_ADMIN} through the company surfaces. With «이 회사에서 내보내기» it can — including by an
 * administrator removing themself. The one predicate, shared by every write that can take {@code TENANT_ADMIN}@T
 * away (export · role PATCH · group member removal · group role revocation · group deletion), so that no write
 * can be used to route around another («take the role away first, then export»):
 *
 * <blockquote>after the write, T has no {@code ACTIVE} operator holding {@code TENANT_ADMIN}@T from any origin
 * (direct · group fan-out · org-node row recorded at T) → {@code 409 TENANT_LAST_ADMIN_REQUIRED}, nothing
 * written.</blockquote>
 *
 * <p>No check when T is {@code '*'} (the platform has no «last admin» of a company), when the write removes no
 * {@code TENANT_ADMIN}@T row of an ACTIVE operator (the target is not an administrator of T), or when T had no
 * administrator to begin with (this write did not cause it). Called BEFORE the write.
 */
@Component
@RequiredArgsConstructor
public class TenantLastAdminGuard {

    /** The company administrator role whose presence the predicate protects (rbac.md role catalog). */
    static final String TENANT_ADMIN_ROLE = "TENANT_ADMIN";

    private final AdminOperatorPort operatorPort;

    /**
     * @param tenantId          the company the write acts in
     * @param removedRow        which of the {@code TENANT_ADMIN}@T rows the write deletes
     * @param addedHolderOrNull the internal id of an ACTIVE operator the write (re)grants {@code TENANT_ADMIN}@T
     *                          to as a direct row, or {@code null}
     * @throws TenantLastAdminRequiredException when the write would leave T without an administrator
     */
    public void requireAdminRemains(String tenantId,
                                    Predicate<AdminOperatorPort.RoleRowView> removedRow,
                                    Long addedHolderOrNull) {
        if (tenantId == null || tenantId.isBlank() || AdminOperator.PLATFORM_TENANT_ID.equals(tenantId)) {
            return;
        }
        List<AdminOperatorPort.RoleRowView> adminRows =
                operatorPort.findActiveRoleRowsInTenant(tenantId, TENANT_ADMIN_ROLE);
        boolean touchesAnAdministrator = false;
        Set<Long> holdersAfter = new HashSet<>();
        for (AdminOperatorPort.RoleRowView row : adminRows) {
            if (removedRow.test(row)) {
                touchesAnAdministrator = true;
            } else {
                holdersAfter.add(row.operatorInternalId());
            }
        }
        if (!touchesAnAdministrator) {
            return;
        }
        if (addedHolderOrNull != null) {
            holdersAfter.add(addedHolderOrNull);
        }
        if (holdersAfter.isEmpty()) {
            throw new TenantLastAdminRequiredException(
                    "Tenant '" + tenantId + "' would be left without an active TENANT_ADMIN — appoint another "
                            + "administrator first");
        }
    }
}
