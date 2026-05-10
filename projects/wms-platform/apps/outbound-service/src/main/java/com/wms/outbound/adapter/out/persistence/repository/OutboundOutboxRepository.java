package com.wms.outbound.adapter.out.persistence.repository;

import com.wms.outbound.adapter.out.persistence.entity.OutboundOutboxEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OutboundOutboxRepository extends JpaRepository<OutboundOutboxEntity, UUID> {

    @Query("SELECT o FROM OutboundOutboxEntity o WHERE o.publishedAt IS NULL ORDER BY o.createdAt ASC")
    List<OutboundOutboxEntity> findPending(Pageable pageable);

    long countByPublishedAtIsNull();

    /**
     * Saga sweeper helper (TASK-BE-050). Returns the most recently-written
     * outbox row matching {@code (aggregateId, eventType)} regardless of
     * publish status, so the sweeper can clone its payload + partition key
     * into a fresh row with a new {@code eventId}.
     *
     * <p>The outbound-outbox table never prunes rows in v1 (publisher
     * marks {@code published_at} but does not delete), so this lookup is
     * deterministic: a saga's first emission row stays available for the
     * lifetime of the saga.
     */
    @Query("""
            SELECT o FROM OutboundOutboxEntity o
            WHERE o.aggregateId = :aggregateId
              AND o.eventType   = :eventType
            ORDER BY o.createdAt DESC
            """)
    List<OutboundOutboxEntity> findLatestByAggregateAndType(
            UUID aggregateId, String eventType, Pageable pageable);

    default Optional<OutboundOutboxEntity> findLatestByAggregateAndType(
            UUID aggregateId, String eventType) {
        List<OutboundOutboxEntity> rows = findLatestByAggregateAndType(
                aggregateId, eventType, org.springframework.data.domain.PageRequest.of(0, 1));
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }
}
