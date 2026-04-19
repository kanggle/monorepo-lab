package com.wms.master.adapter.in.web.dto.response;

import com.wms.master.application.result.SkuResult;
import com.wms.master.domain.model.BaseUom;
import com.wms.master.domain.model.TrackingType;
import com.wms.master.domain.model.WarehouseStatus;
import java.time.Instant;
import java.util.UUID;

public record SkuResponse(
        UUID id,
        String skuCode,
        String name,
        String description,
        String barcode,
        BaseUom baseUom,
        TrackingType trackingType,
        Integer weightGrams,
        Integer volumeMl,
        String hazardClass,
        Integer shelfLifeDays,
        WarehouseStatus status,
        long version,
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        String updatedBy) {

    public static SkuResponse from(SkuResult result) {
        return new SkuResponse(
                result.id(),
                result.skuCode(),
                result.name(),
                result.description(),
                result.barcode(),
                result.baseUom(),
                result.trackingType(),
                result.weightGrams(),
                result.volumeMl(),
                result.hazardClass(),
                result.shelfLifeDays(),
                result.status(),
                result.version(),
                result.createdAt(),
                result.createdBy(),
                result.updatedAt(),
                result.updatedBy());
    }
}
