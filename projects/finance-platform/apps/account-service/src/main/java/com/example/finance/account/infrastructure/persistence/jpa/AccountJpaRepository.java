package com.example.finance.account.infrastructure.persistence.jpa;

import com.example.finance.account.domain.account.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AccountJpaRepository extends JpaRepository<Account, String> {

    // Tenant-scoped — there is no findById(id) without tenant_id (F: multi-tenancy).
    Optional<Account> findByIdAndTenantId(String id, String tenantId);
}
