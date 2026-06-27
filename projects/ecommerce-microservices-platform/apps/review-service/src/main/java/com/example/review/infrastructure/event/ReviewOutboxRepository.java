package com.example.review.infrastructure.event;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Spring Data repository for {@code review_outbox} (TASK-BE-445, outbox v2).
 *
 * <p>Shaped for {@code SpringDataOutboxRowRepository.wrap(...)}: a
 * {@code findPending(Pageable)} that returns unpublished rows in FIFO order and
 * a {@code countByPublishedAtIsNull()} for the pending-count gauge.
 */
public interface ReviewOutboxRepository extends JpaRepository<ReviewOutboxEntity, UUID> {

    @Query("SELECT o FROM ReviewOutboxEntity o WHERE o.publishedAt IS NULL ORDER BY o.occurredAt ASC")
    List<ReviewOutboxEntity> findPending(Pageable pageable);

    long countByPublishedAtIsNull();
}
