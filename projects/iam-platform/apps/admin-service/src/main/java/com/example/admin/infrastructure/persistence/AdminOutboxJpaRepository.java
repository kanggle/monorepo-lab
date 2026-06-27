package com.example.admin.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Spring Data backend for the {@code admin_outbox} table (TASK-BE-452). The two
 * query methods adapt into the generic
 * {@link com.example.messaging.outbox.OutboxRowRepository} via
 * {@link com.example.messaging.outbox.SpringDataOutboxRowRepository#wrap}:
 * {@link #findPending(Pageable)} returns unpublished rows in created-order and
 * {@link #countByPublishedAtIsNull()} backs the pending-count gauge.
 *
 * <p>Both v2 write adapters ({@code OutboxAdminEventPublisher} +
 * {@code OutboxTenantEventPublisher}) persist through this one repository —
 * admin.action.performed and tenant.* share the single {@code admin_outbox} table.
 */
public interface AdminOutboxJpaRepository extends JpaRepository<AdminOutboxJpaEntity, UUID> {

    @Query("""
            SELECT o FROM AdminOutboxJpaEntity o
             WHERE o.publishedAt IS NULL
             ORDER BY o.createdAt ASC
            """)
    List<AdminOutboxJpaEntity> findPending(Pageable pageable);

    long countByPublishedAtIsNull();
}
