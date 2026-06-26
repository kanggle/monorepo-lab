package com.example.finance.account.infrastructure.outbox;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Spring Data backend for the {@code account_outbox} table (TASK-FIN-BE-045).
 * The two query methods adapt into the generic
 * {@link com.example.messaging.outbox.OutboxRowRepository} via
 * {@link com.example.messaging.outbox.SpringDataOutboxRowRepository#wrap}:
 * {@link #findPending(Pageable)} returns unpublished rows in created-order and
 * {@link #countByPublishedAtIsNull()} backs the pending-count gauge.
 */
public interface AccountOutboxJpaRepository extends JpaRepository<AccountOutboxJpaEntity, UUID> {

    @Query("""
            SELECT o FROM AccountOutboxJpaEntity o
             WHERE o.publishedAt IS NULL
             ORDER BY o.createdAt ASC
            """)
    List<AccountOutboxJpaEntity> findPending(Pageable pageable);

    long countByPublishedAtIsNull();
}
