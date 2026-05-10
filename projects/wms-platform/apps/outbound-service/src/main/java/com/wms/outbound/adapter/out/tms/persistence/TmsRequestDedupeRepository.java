package com.wms.outbound.adapter.out.tms.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface TmsRequestDedupeRepository extends JpaRepository<TmsRequestDedupeEntity, UUID> {
}
