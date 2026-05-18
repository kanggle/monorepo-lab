package com.example.finance.account.infrastructure.persistence.jpa;

import com.example.finance.account.domain.balance.Balance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BalanceJpaRepository extends JpaRepository<Balance, String> {

    Optional<Balance> findFirstByAccountIdAndTenantId(String accountId, String tenantId);

    List<Balance> findAllByAccountIdAndTenantId(String accountId, String tenantId);
}
