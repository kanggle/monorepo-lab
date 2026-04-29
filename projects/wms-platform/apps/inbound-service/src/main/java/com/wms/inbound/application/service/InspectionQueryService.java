package com.wms.inbound.application.service;

import com.wms.inbound.application.port.in.QueryInspectionUseCase;
import com.wms.inbound.application.port.out.InspectionPersistencePort;
import com.wms.inbound.application.result.InspectionResult;
import com.wms.inbound.domain.exception.InspectionNotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InspectionQueryService implements QueryInspectionUseCase {

    private final InspectionPersistencePort inspectionPersistence;

    public InspectionQueryService(InspectionPersistencePort inspectionPersistence) {
        this.inspectionPersistence = inspectionPersistence;
    }

    @Override
    @Transactional(readOnly = true)
    public InspectionResult findById(UUID id) {
        return inspectionPersistence.findById(id)
                .map(InspectionService::toResult)
                .orElseThrow(() -> new InspectionNotFoundException(id));
    }

    @Override
    @Transactional(readOnly = true)
    public InspectionResult findByAsnId(UUID asnId) {
        return inspectionPersistence.findByAsnId(asnId)
                .map(InspectionService::toResult)
                .orElseThrow(() -> new InspectionNotFoundException("Inspection not found for ASN: " + asnId));
    }
}
