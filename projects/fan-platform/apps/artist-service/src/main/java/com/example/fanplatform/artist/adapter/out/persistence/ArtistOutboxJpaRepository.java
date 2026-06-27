package com.example.fanplatform.artist.adapter.out.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Spring Data repository for {@code artist_outbox} (TASK-FAN-BE-022, outbox v2).
 *
 * <p>Shaped for {@code SpringDataOutboxRowRepository.wrap(...)}: a
 * {@code findPending(Pageable)} that returns unpublished rows in FIFO order and
 * a {@code countByPublishedAtIsNull()} for the pending-count gauge.
 */
public interface ArtistOutboxJpaRepository extends JpaRepository<ArtistOutboxJpaEntity, UUID> {

    @Query("SELECT o FROM ArtistOutboxJpaEntity o WHERE o.publishedAt IS NULL ORDER BY o.occurredAt ASC")
    List<ArtistOutboxJpaEntity> findPending(Pageable pageable);

    long countByPublishedAtIsNull();
}
