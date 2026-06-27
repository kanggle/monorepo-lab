package com.example.fanplatform.membership.infrastructure.jpa;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Spring Data repository for {@code membership_outbox} (TASK-FAN-BE-020, outbox v2).
 *
 * <p>Shaped for {@code SpringDataOutboxRowRepository.wrap(...)}: a
 * {@code findPending(Pageable)} that returns unpublished rows in FIFO order and
 * a {@code countByPublishedAtIsNull()} for the pending-count gauge.
 */
public interface MembershipOutboxJpaRepository extends JpaRepository<MembershipOutboxJpaEntity, UUID> {

    @Query("SELECT o FROM MembershipOutboxJpaEntity o WHERE o.publishedAt IS NULL ORDER BY o.occurredAt ASC")
    List<MembershipOutboxJpaEntity> findPending(Pageable pageable);

    long countByPublishedAtIsNull();
}
