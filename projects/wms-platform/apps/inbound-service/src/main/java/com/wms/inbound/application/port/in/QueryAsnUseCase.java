package com.wms.inbound.application.port.in;

import com.example.common.page.PageResult;
import com.wms.inbound.application.result.AsnResult;
import com.wms.inbound.application.result.AsnSummaryResult;
import java.util.UUID;

public interface QueryAsnUseCase {

    AsnResult findById(UUID id);

    /**
     * Paginated ASN summaries. {@code page}/{@code size} are pre-validated by
     * the caller (see {@code com.example.common.page.PageQuery}, per
     * {@code inbound-service-api.md} §Pagination — {@code page >= 0},
     * {@code 1 <= size <= 100}).
     */
    PageResult<AsnSummaryResult> list(String status, UUID warehouseId, int page, int size);
}
