package com.wms.inbound.application.port.in;

import com.wms.inbound.application.result.AsnResult;
import com.wms.inbound.application.result.AsnSummaryResult;
import java.util.List;
import java.util.UUID;

public interface QueryAsnUseCase {

    AsnResult findById(UUID id);

    List<AsnSummaryResult> list(String status, UUID warehouseId, int page, int size);

    long count(String status, UUID warehouseId);
}
