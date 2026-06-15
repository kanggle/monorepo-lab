package com.example.account.infrastructure.persistence;

import com.example.account.application.port.AccountQueryPort;
import com.example.account.application.result.AccountDetailResult;
import com.example.account.application.result.AccountSearchResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class AccountQueryPortImpl implements AccountQueryPort {

    /**
     * Platform-scope sentinel (mirrors admin-service {@code AdminOperator.PLATFORM_TENANT_ID}).
     * Only a SUPER_ADMIN reaches account-service with this value — admin-service has
     * already enforced the effective-scope gate (TASK-BE-357).
     */
    private static final String PLATFORM_TENANT_ID = "*";

    private final AccountJpaRepository accountJpaRepository;
    private final ProfileJpaRepository profileJpaRepository;

    @Override
    public AccountSearchResult findAll(String tenantId, int page, int size) {
        // TASK-BE-357: tenant-scoped (was an unscoped all-tenant scan → cross-tenant leak).
        Pageable pageable = PageRequest.of(page, size);
        Page<AccountJpaEntity> jpaPage = PLATFORM_TENANT_ID.equals(tenantId)
                ? accountJpaRepository.findAllAccounts(pageable)
                : accountJpaRepository.findByTenantIdWithStatusFilter(tenantId, null, pageable);
        List<AccountSearchResult.Item> items = jpaPage.getContent().stream()
                .map(AccountQueryPortImpl::toItem)
                .toList();
        return new AccountSearchResult(
                items, jpaPage.getTotalElements(), jpaPage.getNumber(), jpaPage.getSize(), jpaPage.getTotalPages());
    }

    @Override
    public List<AccountSearchResult.Item> findByEmail(String tenantId, String email) {
        // TASK-BE-357: exact email match scoped to the given tenant. Was hard-coded to
        // TenantId.FAN_PLATFORM (the stale `// until TASK-BE-229` residue) — which made
        // every non-fan (e.g. ecommerce) account permanently un-findable by email.
        if (PLATFORM_TENANT_ID.equals(tenantId)) {
            // SUPER_ADMIN cross-tenant: the same email may exist under several tenants.
            return accountJpaRepository.findByEmail(email).stream()
                    .map(AccountQueryPortImpl::toItem)
                    .toList();
        }
        return accountJpaRepository.findByTenantIdAndEmail(tenantId, email)
                .map(e -> List.of(toItem(e)))
                .orElseGet(List::of);
    }

    @Override
    public Optional<AccountDetailResult> findDetailById(String accountId) {
        return accountJpaRepository.findById(accountId)
                .map(account -> {
                    ProfileJpaEntity profile = profileJpaRepository.findByAccountId(accountId).orElse(null);
                    AccountDetailResult.Profile profileResult = profile == null ? null
                            : new AccountDetailResult.Profile(
                                    profile.getDisplayName(), profile.getPhoneNumber());
                    return new AccountDetailResult(
                            account.getId(), account.getEmail(), account.getStatus().name(),
                            account.getCreatedAt(), profileResult);
                });
    }

    private static AccountSearchResult.Item toItem(AccountJpaEntity e) {
        return new AccountSearchResult.Item(
                e.getId(), e.getEmail(), e.getStatus().name(), e.getCreatedAt());
    }
}
