package com.example.security.infrastructure.persistence;

import com.example.security.domain.history.AccountLockHistory;
import com.example.security.domain.repository.AccountLockHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * Persistence adapter for {@link AccountLockHistoryRepository}. Maps the domain
 * {@link AccountLockHistory} to {@link AccountLockHistoryJpaEntity} and delegates to
 * Spring Data. Mirrors {@link LoginHistoryRepositoryImpl}.
 */
@Repository
@RequiredArgsConstructor
public class AccountLockHistoryRepositoryImpl implements AccountLockHistoryRepository {

    private final AccountLockHistoryJpaRepository jpaRepository;

    @Override
    public void save(AccountLockHistory entry) {
        AccountLockHistoryJpaEntity entity = AccountLockHistoryJpaEntity.create(
                entry.getTenantId(),
                entry.getEventId(),
                entry.getAccountId(),
                entry.getReason(),
                entry.getLockedBy(),
                entry.getSource(),
                entry.getOccurredAt());
        jpaRepository.save(entity);
    }
}
