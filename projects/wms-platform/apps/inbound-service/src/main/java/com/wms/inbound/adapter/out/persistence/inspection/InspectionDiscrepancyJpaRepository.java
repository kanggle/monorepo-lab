package com.wms.inbound.adapter.out.persistence.inspection;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface InspectionDiscrepancyJpaRepository extends JpaRepository<InspectionDiscrepancyJpaEntity, UUID> {

    List<InspectionDiscrepancyJpaEntity> findByInspectionId(UUID inspectionId);
}
