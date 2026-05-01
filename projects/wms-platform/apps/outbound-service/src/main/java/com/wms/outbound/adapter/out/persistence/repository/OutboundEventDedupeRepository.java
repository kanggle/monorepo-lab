package com.wms.outbound.adapter.out.persistence.repository;

import com.wms.outbound.adapter.out.persistence.entity.OutboundEventDedupe;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboundEventDedupeRepository extends JpaRepository<OutboundEventDedupe, UUID> {
}
