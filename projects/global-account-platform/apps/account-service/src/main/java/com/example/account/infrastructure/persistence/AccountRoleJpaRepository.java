package com.example.account.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link AccountRoleJpaEntity}. Composite
 * primary key is provided via {@link AccountRoleJpaEntity.AccountRoleId}.
 */
public interface AccountRoleJpaRepository extends JpaRepository<AccountRoleJpaEntity, AccountRoleJpaEntity.AccountRoleId> {

    List<AccountRoleJpaEntity> findByTenantIdAndAccountId(String tenantId, String accountId);

    Optional<AccountRoleJpaEntity> findByTenantIdAndAccountIdAndRoleName(
            String tenantId, String accountId, String roleName);

    @Modifying
    @Query("DELETE FROM AccountRoleJpaEntity r WHERE r.tenantId = :tenantId AND r.accountId = :accountId")
    void deleteByTenantIdAndAccountId(@Param("tenantId") String tenantId,
                                      @Param("accountId") String accountId);

    @Modifying
    @Query("DELETE FROM AccountRoleJpaEntity r "
            + "WHERE r.tenantId = :tenantId AND r.accountId = :accountId AND r.roleName = :roleName")
    int deleteByTenantIdAndAccountIdAndRoleName(@Param("tenantId") String tenantId,
                                                @Param("accountId") String accountId,
                                                @Param("roleName") String roleName);
}
