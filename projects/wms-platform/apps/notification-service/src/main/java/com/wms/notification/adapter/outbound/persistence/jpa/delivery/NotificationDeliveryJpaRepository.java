package com.wms.notification.adapter.outbound.persistence.jpa.delivery;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface NotificationDeliveryJpaRepository extends JpaRepository<NotificationDeliveryJpaEntity, UUID> {

    Optional<NotificationDeliveryJpaEntity> findByDeliveryIdempotencyKey(String idempotencyKey);

    List<NotificationDeliveryJpaEntity> findByEventId(UUID eventId);

    /**
     * Pick PENDING deliveries due for retry. {@code FOR UPDATE SKIP LOCKED}
     * via the JPA lock + Postgres-specific query hint so two scheduler
     * workers cannot double-fire (architecture.md § Concurrency Control).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2")}) // SKIP_LOCKED
    @Query("""
            SELECT d FROM NotificationDeliveryJpaEntity d
             WHERE d.status = 'PENDING'
               AND (d.scheduledRetryAt IS NULL OR d.scheduledRetryAt <= :now)
             ORDER BY d.createdAt
            """)
    List<NotificationDeliveryJpaEntity> findPendingDueForRetry(@Param("now") Instant now,
                                                               Pageable pageable);
}
