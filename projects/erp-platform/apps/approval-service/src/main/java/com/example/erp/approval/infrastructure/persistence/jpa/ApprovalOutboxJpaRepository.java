package com.example.erp.approval.infrastructure.persistence.jpa;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Spring Data backend for the {@code approval_outbox} table (TASK-ERP-BE-025).
 * The two query methods adapt into the generic
 * {@link com.example.messaging.outbox.OutboxRowRepository} via
 * {@link com.example.messaging.outbox.SpringDataOutboxRowRepository#wrap}:
 * {@link #findPending(Pageable)} returns unpublished rows in created-order and
 * {@link #countByPublishedAtIsNull()} backs the pending-count gauge.
 */
public interface ApprovalOutboxJpaRepository extends JpaRepository<ApprovalOutboxJpaEntity, UUID> {

    @Query("""
            SELECT o FROM ApprovalOutboxJpaEntity o
             WHERE o.publishedAt IS NULL
             ORDER BY o.createdAt ASC
            """)
    List<ApprovalOutboxJpaEntity> findPending(Pageable pageable);

    long countByPublishedAtIsNull();
}
