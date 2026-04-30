package com.example.account.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccountStatusHistoryJpaRepository
        extends JpaRepository<AccountStatusHistoryJpaEntity, Long> {

    List<AccountStatusHistoryJpaEntity> findByAccountIdOrderByOccurredAtDesc(String accountId);

    Optional<AccountStatusHistoryJpaEntity> findTopByAccountIdOrderByOccurredAtDesc(String accountId);
}
