package com.wms.master.application.port.in;

import com.example.common.page.PageResult;
import com.wms.master.application.query.ListPartnersQuery;
import com.wms.master.application.result.PartnerResult;
import java.util.UUID;

/**
 * Inbound port for Partner read-side operations.
 */
public interface PartnerQueryUseCase {

    PartnerResult findById(UUID id);

    /**
     * Lookup by business partner_code. The code is stored as-supplied (no
     * case normalization, distinct from SKU); caller must pass the exact form.
     */
    PartnerResult findByCode(String partnerCode);

    PageResult<PartnerResult> list(ListPartnersQuery query);
}
