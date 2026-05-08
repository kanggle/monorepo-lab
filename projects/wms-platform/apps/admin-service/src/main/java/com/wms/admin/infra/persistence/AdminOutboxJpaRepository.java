package com.wms.admin.infra.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AdminOutboxJpaRepository extends JpaRepository<AdminOutboxJpaEntity, UUID> {

    @Query("""
            SELECT o FROM AdminOutboxJpaEntity o
            WHERE o.publishedAt IS NULL
            ORDER BY o.createdAt
            """)
    List<AdminOutboxJpaEntity> findPending(Pageable pageable);

    long countByPublishedAtIsNull();
}
