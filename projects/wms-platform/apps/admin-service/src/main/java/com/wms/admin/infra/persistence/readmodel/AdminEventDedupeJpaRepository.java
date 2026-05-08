package com.wms.admin.infra.persistence.readmodel;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AdminEventDedupeJpaRepository
        extends JpaRepository<AdminEventDedupeJpaEntity, UUID> {

    @Query("SELECT COUNT(d) FROM AdminEventDedupeJpaEntity d WHERE d.outcome = :outcome")
    long countByOutcome(String outcome);
}
