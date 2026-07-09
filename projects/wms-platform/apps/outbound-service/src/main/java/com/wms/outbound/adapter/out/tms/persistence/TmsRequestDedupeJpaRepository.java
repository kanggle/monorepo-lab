package com.wms.outbound.adapter.out.tms.persistence;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface TmsRequestDedupeJpaRepository extends JpaRepository<TmsRequestDedupeEntity, UUID> {

    /**
     * Insert the snapshot iff {@code requestId} is not already present, in a
     * single atomic native statement — the documented "first writer wins".
     *
     * <p>Not {@code save(...)}: for a caller-assigned non-null {@code @Id} with no
     * {@code @Version}, Spring Data routes {@code save()} to {@code merge()}, an
     * SELECT-then-UPDATE. On this table the UPDATE is rejected by the W2
     * append-only trigger (raised at commit, outside the adapter's catch), so a
     * concurrent re-fire threw instead of being swallowed (TASK-BE-488). The
     * unconditional {@code ON CONFLICT DO NOTHING} insert keeps the first row and
     * no-ops the loser without touching the trigger. {@code response_snapshot} is
     * {@code JSONB}, so the text parameter is cast explicitly.
     *
     * @return 1 when the row was inserted, 0 when the requestId already existed
     */
    @Modifying
    @Query(value = """
            INSERT INTO tms_request_dedupe (request_id, sent_at, response_snapshot)
            VALUES (:requestId, :sentAt, CAST(:responseSnapshot AS jsonb))
            ON CONFLICT (request_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("requestId") UUID requestId,
                       @Param("sentAt") Instant sentAt,
                       @Param("responseSnapshot") String responseSnapshot);
}
