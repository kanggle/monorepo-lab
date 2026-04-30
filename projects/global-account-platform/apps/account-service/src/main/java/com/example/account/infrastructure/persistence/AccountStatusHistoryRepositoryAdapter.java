package com.example.account.infrastructure.persistence;

import com.example.account.domain.history.AccountStatusHistoryEntry;
import com.example.account.domain.repository.AccountStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class AccountStatusHistoryRepositoryAdapter implements AccountStatusHistoryRepository {

    private final AccountStatusHistoryJpaRepository jpaRepository;

    @Override
    public AccountStatusHistoryEntry save(AccountStatusHistoryEntry entry) {
        AccountStatusHistoryJpaEntity entity = AccountStatusHistoryJpaEntity.fromDomain(entry);
        AccountStatusHistoryJpaEntity saved = jpaRepository.save(entity);
        return saved.toDomain();
    }

    @Override
    public List<AccountStatusHistoryEntry> findByAccountIdOrderByOccurredAtDesc(String accountId) {
        return jpaRepository.findByAccountIdOrderByOccurredAtDesc(accountId)
                .stream()
                .map(AccountStatusHistoryJpaEntity::toDomain)
                .toList();
    }

    @Override
    public Optional<AccountStatusHistoryEntry> findTopByAccountIdOrderByOccurredAtDesc(String accountId) {
        return jpaRepository.findTopByAccountIdOrderByOccurredAtDesc(accountId)
                .map(AccountStatusHistoryJpaEntity::toDomain);
    }
}
