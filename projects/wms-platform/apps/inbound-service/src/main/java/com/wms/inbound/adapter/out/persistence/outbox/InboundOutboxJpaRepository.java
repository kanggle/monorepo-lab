package com.wms.inbound.adapter.out.persistence.outbox;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface InboundOutboxJpaRepository extends JpaRepository<InboundOutboxJpaEntity, UUID> {

    @Query("SELECT o FROM InboundOutboxJpaEntity o WHERE o.publishedAt IS NULL ORDER BY o.createdAt ASC")
    List<InboundOutboxJpaEntity> findPending(Pageable pageable);

    long countByPublishedAtIsNull();
}
