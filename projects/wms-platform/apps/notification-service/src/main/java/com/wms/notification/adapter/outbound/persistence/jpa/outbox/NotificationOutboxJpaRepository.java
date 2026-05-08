package com.wms.notification.adapter.outbound.persistence.jpa.outbox;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface NotificationOutboxJpaRepository extends JpaRepository<NotificationOutboxJpaEntity, UUID> {

    @Query("""
            SELECT o FROM NotificationOutboxJpaEntity o
             WHERE o.publishedAt IS NULL
             ORDER BY o.createdAt ASC
            """)
    List<NotificationOutboxJpaEntity> findPending(Pageable pageable);

    long countByPublishedAtIsNull();
}
