package com.example.account.application.service;

import com.example.account.domain.account.AccountRole;
import com.example.account.domain.repository.AccountRoleRepository;
import com.example.account.domain.tenant.TenantId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * TASK-BE-368 (ADR-MONO-033 S2 / ADR-032 step 2.1): Read-only roles lookup for
 * the internal roles read endpoint.
 *
 * <p>Returns the role names assigned to the given account within the tenant.
 * A foreign or missing account yields an empty list (enumeration-safe — no 404).
 * The caller (auth-service) fail-softs on an empty result.
 *
 * <p>Net-zero: no audit row, no outbox event, no mutation.
 */
@Service
@RequiredArgsConstructor
public class GetAccountRolesUseCase {

    private final AccountRoleRepository accountRoleRepository;

    /**
     * Returns all role names assigned to {@code accountId} within {@code tenantId}.
     * Returns an empty list when the account has no roles or does not exist in this tenant.
     *
     * @param tenantId  the tenant scope (slug string)
     * @param accountId the account identifier
     * @return list of role name strings (possibly empty, never null)
     */
    @Transactional(readOnly = true)
    public List<String> execute(String tenantId, String accountId) {
        TenantId tid = new TenantId(tenantId);
        return accountRoleRepository.findByTenantIdAndAccountId(tid, accountId)
                .stream()
                .map(AccountRole::getRoleName)
                .toList();
    }
}
