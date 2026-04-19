package com.wms.master.application.command;

import com.wms.master.domain.model.BaseUom;
import com.wms.master.domain.model.TrackingType;

public record CreateSkuCommand(
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
        String actorId) {
}
