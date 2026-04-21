package com.example.messaging.outbox;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface OutboxJpaRepository extends JpaRepository<OutboxJpaEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM OutboxJpaEntity o WHERE o.status = 'PENDING' ORDER BY o.createdAt ASC")
    List<OutboxJpaEntity> findPendingWithLock(Pageable pageable);
}
