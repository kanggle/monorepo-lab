package com.wms.outbound.adapter.out.persistence.repository;

import com.wms.outbound.adapter.out.persistence.entity.OutboundSagaEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboundSagaRepository extends JpaRepository<OutboundSagaEntity, UUID> {

    Optional<OutboundSagaEntity> findByOrderId(UUID orderId);

    Optional<OutboundSagaEntity> findByPickingRequestId(UUID pickingRequestId);

    /**
     * Bulk lookup used by {@code OrderQueryService.list} to avoid N+1.
     */
    List<OutboundSagaEntity> findByOrderIdIn(Collection<UUID> orderIds);

    /**
     * Saga sweeper query (TASK-BE-050).
     *
     * <p>Returns sagas in {@code status} whose {@code updated_at}
     * (= {@code last_transition_at}) is older than
     * {@code now() - grace_seconds} on the **database clock**. Using DB
     * {@code now()} avoids JVM-side clock skew between sweeper replicas.
     *
     * <p>Native SQL because JPQL has no portable interval-arithmetic
     * primitive against the DB clock. Postgres-only — outbound-service
     * does not run on H2 in production paths (the standalone profile
     * disables the sweeper entirely).
     *
     * <p>Caller passes the full grace period in seconds; H2 / Postgres
     * casting via {@code make_interval(secs => :graceSeconds)} keeps the
     * predicate index-friendly on the partial index {@code idx_outbound_saga_sweeper_candidates}.
     */
    @Query(value = """
            SELECT * FROM outbound_saga
            WHERE status = :status
              AND updated_at < (now() - make_interval(secs => CAST(:graceSeconds AS double precision)))
            ORDER BY updated_at ASC
            LIMIT :batchLimit
            """, nativeQuery = true)
    List<OutboundSagaEntity> findStuckByStatusUsingDbClock(
            @Param("status") String status,
            @Param("graceSeconds") long graceSeconds,
            @Param("batchLimit") int batchLimit);
}
