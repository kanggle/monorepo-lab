package com.example.account.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AccountRoleJpaRepository extends JpaRepository<AccountRoleJpaEntity, Long> {

    List<AccountRoleJpaEntity> findByTenantIdAndAccountId(String tenantId, String accountId);

    void deleteByTenantIdAndAccountId(String tenantId, String accountId);
}
