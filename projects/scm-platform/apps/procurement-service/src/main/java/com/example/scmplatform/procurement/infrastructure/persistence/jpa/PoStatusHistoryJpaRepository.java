package com.example.scmplatform.procurement.infrastructure.persistence.jpa;

import com.example.scmplatform.procurement.domain.po.repository.ReceivedTransitionRow;
import com.example.scmplatform.procurement.domain.po.status.PoStatus;
import com.example.scmplatform.procurement.domain.po.status.PoStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface PoStatusHistoryJpaRepository extends JpaRepository<PoStatusHistory, Long> {

    /**
     * Metrics (TASK-MONO-810): transitions to {@code toStatus} in {@code [from, to)} — one row
     * per transition. Served by {@code idx_psh_tenant_to_status_occurred} (V7).
     */
    @Query("""
            SELECT new com.example.scmplatform.procurement.domain.po.repository.ReceivedTransitionRow(
                       h.poId, h.occurredAt)
            FROM PoStatusHistory h
            WHERE h.tenantId = :tenantId
              AND h.toStatus = :toStatus
              AND h.occurredAt >= :from
              AND h.occurredAt < :to
            """)
    List<ReceivedTransitionRow> findTransitionsIn(@Param("tenantId") String tenantId,
                                                  @Param("toStatus") PoStatus toStatus,
                                                  @Param("from") Instant from,
                                                  @Param("to") Instant to);
}
