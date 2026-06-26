package com.wms.master.adapter.out.persistence.outbox;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Spring Data repository for {@code master_outbox} (TASK-BE-438, outbox v2).
 *
 * <p>Shaped for {@code SpringDataOutboxRowRepository.wrap(...)}: a
 * {@code findPending(Pageable)} that returns unpublished rows in FIFO order and
 * a {@code countByPublishedAtIsNull()} for the pending-count gauge.
 */
public interface MasterOutboxRepository extends JpaRepository<MasterOutboxEntity, UUID> {

    @Query("SELECT o FROM MasterOutboxEntity o WHERE o.publishedAt IS NULL ORDER BY o.occurredAt ASC")
    List<MasterOutboxEntity> findPending(Pageable pageable);

    long countByPublishedAtIsNull();
}
