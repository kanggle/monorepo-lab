package com.example.messaging.outbox;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface OutboxJpaRepository extends JpaRepository<OutboxJpaEntity, Long> {

    @Query(value = "SELECT * FROM outbox WHERE status = 'PENDING' ORDER BY created_at ASC LIMIT :#{#pageable.pageSize} FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<OutboxJpaEntity> findPendingWithLock(Pageable pageable);
}
