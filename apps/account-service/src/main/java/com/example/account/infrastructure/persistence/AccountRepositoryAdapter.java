package com.example.account.infrastructure.persistence;

import com.example.account.domain.account.Account;
import com.example.account.domain.repository.AccountRepository;
import com.example.account.domain.status.AccountStatus;
import com.example.account.domain.tenant.TenantId;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class AccountRepositoryAdapter implements AccountRepository {

    private final AccountJpaRepository jpaRepository;

    @Override
    public Account save(Account account) {
        AccountJpaEntity entity = AccountJpaEntity.fromDomain(account);
        AccountJpaEntity saved = jpaRepository.save(entity);
        return saved.toDomain();
    }

    @Override
    public Optional<Account> findById(TenantId tenantId, String id) {
        return jpaRepository.findByTenantIdAndId(tenantId.value(), id)
                .map(AccountJpaEntity::toDomain);
    }

    @Override
    public Optional<Account> findByEmail(TenantId tenantId, String email) {
        return jpaRepository.findByTenantIdAndEmail(tenantId.value(), email)
                .map(AccountJpaEntity::toDomain);
    }

    @Override
    public boolean existsByEmail(TenantId tenantId, String email) {
        return jpaRepository.existsByTenantIdAndEmail(tenantId.value(), email);
    }

    @Override
    public ProvisioningPage<Account> findAllByTenantId(TenantId tenantId, AccountStatus status, int page, int size) {
        Page<AccountJpaEntity> jpaPage = jpaRepository.findByTenantIdWithStatusFilter(
                tenantId.value(), status, PageRequest.of(page, size));
        return new ProvisioningPage<>(
                jpaPage.getContent().stream().map(AccountJpaEntity::toDomain).toList(),
                jpaPage.getTotalElements(),
                jpaPage.getNumber(),
                jpaPage.getSize(),
                jpaPage.getTotalPages()
        );
    }
}
