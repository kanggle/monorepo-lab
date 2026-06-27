package com.example.erp.masterdata.infrastructure.persistence.jpa;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Spring Data backend for the {@code masterdata_outbox} table (TASK-ERP-BE-026).
 * The two query methods adapt into the generic
 * {@link com.example.messaging.outbox.OutboxRowRepository} via
 * {@link com.example.messaging.outbox.SpringDataOutboxRowRepository#wrap}:
 * {@link #findPending(Pageable)} returns unpublished rows in created-order and
 * {@link #countByPublishedAtIsNull()} backs the pending-count gauge.
 */
public interface MasterdataOutboxJpaRepository extends JpaRepository<MasterdataOutboxJpaEntity, UUID> {

    @Query("""
            SELECT o FROM MasterdataOutboxJpaEntity o
             WHERE o.publishedAt IS NULL
             ORDER BY o.createdAt ASC
            """)
    List<MasterdataOutboxJpaEntity> findPending(Pageable pageable);

    long countByPublishedAtIsNull();
}
