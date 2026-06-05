package com.example.account.domain.repository;

import com.example.account.domain.history.AccountStatusHistoryEntry;

import java.util.List;
import java.util.Optional;

public interface AccountStatusHistoryRepository {

    AccountStatusHistoryEntry save(AccountStatusHistoryEntry entry);

    List<AccountStatusHistoryEntry> findByAccountIdOrderByOccurredAtDesc(String accountId);

    Optional<AccountStatusHistoryEntry> findTopByAccountIdOrderByOccurredAtDesc(String accountId);
}
