package com.wms.admin.readmodel.inbound;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InspectionSummaryRepository extends JpaRepository<InspectionSummaryEntity, UUID> {
}
