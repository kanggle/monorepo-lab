package com.wms.inbound.application.port.out;

import com.wms.inbound.domain.model.Inspection;
import com.wms.inbound.domain.model.InspectionDiscrepancy;
import java.util.Optional;
import java.util.UUID;

public interface InspectionPersistencePort {

    Inspection save(Inspection inspection);

    Optional<Inspection> findById(UUID id);

    Optional<Inspection> findByAsnId(UUID asnId);

    Optional<InspectionDiscrepancy> findDiscrepancyById(UUID discrepancyId);

    InspectionDiscrepancy saveDiscrepancy(InspectionDiscrepancy discrepancy);
}
