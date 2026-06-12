package com.example.erp.notification.infrastructure.persistence.jpa;

import com.example.erp.notification.domain.delivery.DeliveryStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface NotificationDeliveryJpaRepository
        extends JpaRepository<NotificationDeliveryJpaEntity, String> {

    /**
     * Ids of deliveries due for an external attempt: PENDING with a due
     * {@code scheduledRetryAt}, oldest-due first (TASK-ERP-BE-020). The
     * {@code (status, scheduled_retry_at)} index backs this query.
     */
    @Query("""
            SELECT d.id FROM NotificationDeliveryJpaEntity d
            WHERE d.status = :status
              AND d.scheduledRetryAt IS NOT NULL
              AND d.scheduledRetryAt <= :now
            ORDER BY d.scheduledRetryAt ASC
            """)
    List<String> findDueDeliveryIds(@Param("status") DeliveryStatus status,
                                    @Param("now") Instant now,
                                    Pageable pageable);
}
