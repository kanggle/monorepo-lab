package com.example.account.infrastructure.persistence;

import com.example.account.domain.account.AccountRole;
import com.example.account.domain.repository.AccountRoleRepository;
import com.example.account.domain.tenant.TenantId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class AccountRoleRepositoryAdapter implements AccountRoleRepository {

    private final AccountRoleJpaRepository jpaRepository;

    @Override
    public AccountRole save(AccountRole role) {
        AccountRoleJpaEntity entity = AccountRoleJpaEntity.fromDomain(role);
        return jpaRepository.save(entity).toDomain();
    }

    @Override
    public List<AccountRole> findByTenantIdAndAccountId(TenantId tenantId, String accountId) {
        return jpaRepository.findByTenantIdAndAccountId(tenantId.value(), accountId)
                .stream()
                .map(AccountRoleJpaEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public void deleteAllByTenantIdAndAccountId(TenantId tenantId, String accountId) {
        jpaRepository.deleteByTenantIdAndAccountId(tenantId.value(), accountId);
    }
}
