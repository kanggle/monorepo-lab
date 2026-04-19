package com.wms.master.application.result;

import com.wms.master.domain.model.BaseUom;
import com.wms.master.domain.model.Sku;
import com.wms.master.domain.model.TrackingType;
import com.wms.master.domain.model.WarehouseStatus;
import java.time.Instant;
import java.util.UUID;

public record SkuResult(
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

    public static SkuResult from(Sku sku) {
        return new SkuResult(
                sku.getId(),
                sku.getSkuCode(),
                sku.getName(),
                sku.getDescription(),
                sku.getBarcode(),
                sku.getBaseUom(),
                sku.getTrackingType(),
                sku.getWeightGrams(),
                sku.getVolumeMl(),
                sku.getHazardClass(),
                sku.getShelfLifeDays(),
                sku.getStatus(),
                sku.getVersion(),
                sku.getCreatedAt(),
                sku.getCreatedBy(),
                sku.getUpdatedAt(),
                sku.getUpdatedBy());
    }
}
