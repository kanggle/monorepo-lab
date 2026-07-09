package com.wms.inventory.adapter.out.persistence.dedupe;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventDedupeJpaRepository extends JpaRepository<EventDedupeJpaEntity, UUID> {

    /**
     * Insert a dedupe row iff {@code eventId} is not already present, in a single
     * atomic native statement.
     *
     * <p>This is deliberately <em>not</em> {@code save(...)}: for an entity whose
     * {@code @Id} is a caller-assigned non-null UUID and which has no
     * {@code @Version}, Spring Data's {@code save()} treats the entity as
     * detached and routes to {@code EntityManager.merge()} — an
     * {@code SELECT}-then-{@code UPDATE} upsert that silently succeeds on a
     * duplicate PK instead of colliding. That defeated dedupe entirely
     * (TASK-BE-488). {@code INSERT … ON CONFLICT DO NOTHING} inserts exactly
     * once and returns the affected-row count (1 = first sighting, 0 =
     * duplicate), never throwing — so the surrounding {@code MANDATORY}
     * consumer transaction is never poisoned with rollback-only state.
     *
     * @return 1 when the row was inserted, 0 when the eventId already existed
     */
    @Modifying
    @Query(value = """
            INSERT INTO inventory_event_dedupe (event_id, event_type, processed_at, outcome)
            VALUES (:eventId, :eventType, :processedAt, :outcome)
            ON CONFLICT (event_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("eventId") UUID eventId,
                       @Param("eventType") String eventType,
                       @Param("processedAt") Instant processedAt,
                       @Param("outcome") String outcome);
}
