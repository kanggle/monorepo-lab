package com.example.account.application.service;

import com.example.account.application.exception.AccountNotFoundException;
import com.example.account.application.exception.TenantNotFoundException;
import com.example.account.application.result.ProvisionedAccountDetailResult;
import com.example.account.application.result.ProvisionedAccountListResult;
import com.example.account.domain.account.Account;
import com.example.account.domain.account.AccountRole;
import com.example.account.domain.repository.AccountRepository;
import com.example.account.domain.repository.AccountRoleRepository;
import com.example.account.domain.repository.ProfileRepository;
import com.example.account.domain.repository.TenantRepository;
import com.example.account.domain.status.AccountStatus;
import com.example.account.domain.tenant.TenantId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * TASK-BE-231: Read-only queries for the internal provisioning API.
 * Provides paginated listing and single-account detail scoped to a tenant.
 */
@Service
@RequiredArgsConstructor
public class TenantAccountQueryUseCase {

    private static final int MAX_PAGE_SIZE = 100;

    private final TenantRepository tenantRepository;
    private final AccountRepository accountRepository;
    private final AccountRoleRepository accountRoleRepository;
    private final ProfileRepository profileRepository;

    @Transactional(readOnly = true)
    public ProvisionedAccountListResult listAccounts(String tenantIdStr, AccountStatus statusFilter,
                                                      int page, int size) {
        if (size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size must be <= " + MAX_PAGE_SIZE);
        }

        TenantId tenantId = new TenantId(tenantIdStr);
        tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantIdStr));

        AccountRepository.ProvisioningPage<Account> accountPage =
                accountRepository.findAllByTenantId(tenantId, statusFilter, page, size);

        List<ProvisionedAccountListResult.Item> items = accountPage.content().stream()
                .map(account -> {
                    List<String> roles = accountRoleRepository
                            .findByTenantIdAndAccountId(tenantId, account.getId())
                            .stream().map(AccountRole::getRoleName).toList();
                    return new ProvisionedAccountListResult.Item(
                            account.getId(),
                            account.getTenantId().value(),
                            account.getEmail(),
                            account.getStatus().name(),
                            roles,
                            account.getCreatedAt()
                    );
                })
                .toList();

        return new ProvisionedAccountListResult(
                items,
                accountPage.totalElements(),
                accountPage.page(),
                accountPage.size(),
                accountPage.totalPages()
        );
    }

    @Transactional(readOnly = true)
    public ProvisionedAccountDetailResult getAccount(String tenantIdStr, String accountId) {
        TenantId tenantId = new TenantId(tenantIdStr);
        tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantIdStr));

        Account account = accountRepository.findById(tenantId, accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        List<String> roles = accountRoleRepository
                .findByTenantIdAndAccountId(tenantId, accountId)
                .stream().map(AccountRole::getRoleName).toList();

        String displayName = profileRepository.findByAccountId(accountId)
                .map(p -> p.getDisplayName())
                .orElse(null);

        return ProvisionedAccountDetailResult.from(account, displayName, roles);
    }
}
