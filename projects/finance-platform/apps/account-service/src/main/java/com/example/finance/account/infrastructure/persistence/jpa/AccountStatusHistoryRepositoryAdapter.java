package com.example.finance.account.infrastructure.persistence.jpa;

import com.example.finance.account.domain.account.status.AccountStatusHistory;
import com.example.finance.account.domain.account.status.AccountStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Append-only account status transition log — only {@code save} (F6). */
@Component
@RequiredArgsConstructor
public class AccountStatusHistoryRepositoryAdapter
        implements AccountStatusHistoryRepository {

    private final AccountStatusHistoryJpaRepository jpa;

    @Override
    public AccountStatusHistory save(AccountStatusHistory row) {
        return jpa.save(row);
    }
}
