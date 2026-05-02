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

    /**
     * TASK-BE-255: Insert the role only when the (tenant, account, role) triple
     * is not already present. Returns {@code true} when a new row was written.
     */
    @Override
    @Transactional
    public boolean addIfAbsent(AccountRole role) {
        var existing = jpaRepository.findByTenantIdAndAccountIdAndRoleName(
                role.getTenantId().value(), role.getAccountId(), role.getRoleName());
        if (existing.isPresent()) {
            return false;
        }
        jpaRepository.save(AccountRoleJpaEntity.fromDomain(role));
        return true;
    }

    /**
     * TASK-BE-255: Remove the role only when present. Returns {@code true} when
     * a row was deleted.
     */
    @Override
    @Transactional
    public boolean removeIfPresent(TenantId tenantId, String accountId, String roleName) {
        int deleted = jpaRepository.deleteByTenantIdAndAccountIdAndRoleName(
                tenantId.value(), accountId, roleName);
        return deleted > 0;
    }
}
