package com.wms.inbound.application.port.in;

import com.wms.inbound.application.result.InspectionResult;
import java.util.UUID;

public interface QueryInspectionUseCase {

    InspectionResult findById(UUID id);

    InspectionResult findByAsnId(UUID asnId);
}
