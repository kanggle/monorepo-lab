package com.wms.outbound.adapter.out.persistence.repository;

import com.wms.outbound.adapter.out.persistence.entity.OutboundOutboxEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OutboundOutboxRepository extends JpaRepository<OutboundOutboxEntity, UUID> {

    @Query("SELECT o FROM OutboundOutboxEntity o WHERE o.publishedAt IS NULL ORDER BY o.createdAt ASC")
    List<OutboundOutboxEntity> findPending(Pageable pageable);

    long countByPublishedAtIsNull();
}
