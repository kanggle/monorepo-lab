package com.example.fanplatform.community.infrastructure.jpa;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Spring Data repository for {@code community_outbox} (TASK-FAN-BE-021, outbox v2).
 *
 * <p>Shaped for {@code SpringDataOutboxRowRepository.wrap(...)}: a
 * {@code findPending(Pageable)} that returns unpublished rows in FIFO order and
 * a {@code countByPublishedAtIsNull()} for the pending-count gauge.
 */
public interface CommunityOutboxJpaRepository extends JpaRepository<CommunityOutboxJpaEntity, UUID> {

    @Query("SELECT o FROM CommunityOutboxJpaEntity o WHERE o.publishedAt IS NULL ORDER BY o.occurredAt ASC")
    List<CommunityOutboxJpaEntity> findPending(Pageable pageable);

    long countByPublishedAtIsNull();
}
