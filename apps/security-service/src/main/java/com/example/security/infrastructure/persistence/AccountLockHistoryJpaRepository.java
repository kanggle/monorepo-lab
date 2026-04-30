package com.example.security.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccountLockHistoryJpaRepository extends JpaRepository<AccountLockHistoryJpaEntity, Long> {

    Optional<AccountLockHistoryJpaEntity> findByEventId(String eventId);

    List<AccountLockHistoryJpaEntity> findByAccountIdOrderByOccurredAtDesc(String accountId);
}
