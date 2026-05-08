package com.wms.admin.infra.persistence.readmodel;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdminEventDedupeJpaRepository
        extends JpaRepository<AdminEventDedupeJpaEntity, UUID> {

    @Query("SELECT COUNT(d) FROM AdminEventDedupeJpaEntity d WHERE d.outcome = :outcome")
    long countByOutcome(String outcome);

    /**
     * Returns one row per {@code eventType} containing the latest
     * {@code processed_at}. Empty result set means none of the supplied
     * eventTypes have been observed yet.
     */
    @Query("SELECT d.eventType, MAX(d.processedAt) FROM AdminEventDedupeJpaEntity d "
            + "WHERE d.eventType IN :eventTypes "
            + "GROUP BY d.eventType")
    List<Object[]> findMaxProcessedAtByEventTypes(@Param("eventTypes") Collection<String> eventTypes);
}
