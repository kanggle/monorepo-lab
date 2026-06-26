package com.example.order.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Spring Data repository for {@code order_outbox} (TASK-BE-448, outbox v2).
 *
 * <p>Shaped for {@code SpringDataOutboxRowRepository.wrap(...)}: a
 * {@code findPending(Pageable)} returning unpublished rows in FIFO order and a
 * {@code countByPublishedAtIsNull()} for the pending-count gauge.
 */
public interface OrderOutboxRepository extends JpaRepository<OrderOutboxEntity, UUID> {

    @Query("SELECT o FROM OrderOutboxEntity o WHERE o.publishedAt IS NULL ORDER BY o.occurredAt ASC")
    List<OrderOutboxEntity> findPending(Pageable pageable);

    long countByPublishedAtIsNull();
}
