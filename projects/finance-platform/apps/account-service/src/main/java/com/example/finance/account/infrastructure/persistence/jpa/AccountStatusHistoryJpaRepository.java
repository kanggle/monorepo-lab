package com.example.finance.account.infrastructure.persistence.jpa;

import com.example.finance.account.domain.account.status.AccountStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountStatusHistoryJpaRepository
        extends JpaRepository<AccountStatusHistory, Long> {
}
