package com.wms.inbound.adapter.out.persistence.dedupe;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventDedupeJpaRepository extends JpaRepository<EventDedupeJpaEntity, UUID> {
}
