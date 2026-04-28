package com.wms.inventory.adapter.out.persistence.outbox;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface InventoryOutboxJpaRepository extends JpaRepository<InventoryOutboxJpaEntity, UUID> {

    @Query("""
            SELECT o FROM InventoryOutboxJpaEntity o
             WHERE o.publishedAt IS NULL
             ORDER BY o.createdAt ASC
            """)
    List<InventoryOutboxJpaEntity> findPending(Pageable pageable);

    long countByPublishedAtIsNull();
}
