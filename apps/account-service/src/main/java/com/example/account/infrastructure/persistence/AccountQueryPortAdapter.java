package com.example.account.infrastructure.persistence;

import com.example.account.application.port.AccountQueryPort;
import com.example.account.application.result.AccountDetailResult;
import com.example.account.application.result.AccountSearchResult;
import com.example.account.domain.tenant.TenantId;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class AccountQueryPortAdapter implements AccountQueryPort {

    private final AccountJpaRepository accountJpaRepository;
    private final ProfileJpaRepository profileJpaRepository;

    @Override
    public AccountSearchResult findAll(Pageable pageable) {
        Page<AccountJpaEntity> page = accountJpaRepository.findAllAccounts(pageable);
        List<AccountSearchResult.Item> items = page.getContent().stream()
                .map(e -> new AccountSearchResult.Item(
                        e.getId(), e.getEmail(), e.getStatus().name(), e.getCreatedAt()))
                .toList();
        return new AccountSearchResult(
                items, page.getTotalElements(), page.getNumber(), page.getSize(), page.getTotalPages());
    }

    @Override
    public Optional<AccountSearchResult.Item> findByEmail(String email) {
        // TASK-BE-228: tenant context is fixed to FAN_PLATFORM until TASK-BE-229.
        // The admin search query port returns results within the default tenant.
        return accountJpaRepository.findByTenantIdAndEmail(TenantId.FAN_PLATFORM.value(), email)
                .map(e -> new AccountSearchResult.Item(
                        e.getId(), e.getEmail(), e.getStatus().name(), e.getCreatedAt()));
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
}
