package com.example.admin.infrastructure.persistence;

import com.example.admin.application.port.TenantEntryPolicyPort;
import com.example.admin.domain.rbac.AdminOperator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * TASK-MONO-771 — JPA-backed {@link TenantEntryPolicyPort}. Read failures propagate (never swallowed into
 * «off»): the callers turn them into a fail-closed refusal.
 */
@Component
@RequiredArgsConstructor
public class TenantEntryPolicyPortImpl implements TenantEntryPolicyPort {

    private final TenantEntryPolicyJpaRepository repository;

    @Override
    @Transactional(readOnly = true)
    public Set<String> findTenantsRequiringMfa(Collection<String> tenantIds) {
        Set<String> candidates = new LinkedHashSet<>();
        if (tenantIds != null) {
            for (String t : tenantIds) {
                if (t != null && !t.isBlank() && !AdminOperator.PLATFORM_TENANT_ID.equals(t)) {
                    candidates.add(t);
                }
            }
        }
        if (candidates.isEmpty()) {
            return Set.of(); // nothing that can hold a policy — no read issued
        }
        return Set.copyOf(repository.findTenantIdsRequiringMfa(candidates));
    }
}
