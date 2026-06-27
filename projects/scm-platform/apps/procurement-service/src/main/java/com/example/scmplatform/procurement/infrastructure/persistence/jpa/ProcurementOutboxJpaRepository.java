package com.example.scmplatform.procurement.infrastructure.persistence.jpa;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Spring Data repository for {@code procurement_outbox} (TASK-SCM-BE-032, outbox v2).
 *
 * <p>Shaped for {@code SpringDataOutboxRowRepository.wrap(...)}: a
 * {@code findPending(Pageable)} that returns unpublished rows in FIFO order and
 * a {@code countByPublishedAtIsNull()} for the pending-count gauge.
 */
public interface ProcurementOutboxJpaRepository extends JpaRepository<ProcurementOutboxJpaEntity, UUID> {

    @Query("SELECT o FROM ProcurementOutboxJpaEntity o WHERE o.publishedAt IS NULL ORDER BY o.occurredAt ASC")
    List<ProcurementOutboxJpaEntity> findPending(Pageable pageable);

    long countByPublishedAtIsNull();
}
