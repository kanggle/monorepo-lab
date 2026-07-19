package com.example.scmplatform.demandplanning.adapter.inbound.web.dto;

import com.example.scmplatform.demandplanning.domain.model.SkuSupplierMapping;

public record MappingResponse(
        String skuCode,
        String supplierId,
        int defaultOrderQty,
        int leadTimeDays,
        String currency
) {
    public static MappingResponse from(SkuSupplierMapping m) {
        return new MappingResponse(m.getSkuCode(), m.getSupplierId(),
                m.getDefaultOrderQty(), m.getLeadTimeDays(), m.getCurrency());
    }
}
