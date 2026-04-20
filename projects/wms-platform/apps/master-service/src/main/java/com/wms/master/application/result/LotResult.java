package com.wms.master.application.result;

import com.wms.master.domain.model.Lot;
import com.wms.master.domain.model.LotStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record LotResult(
        UUID id,
        UUID skuId,
        String lotNo,
        LocalDate manufacturedDate,
        LocalDate expiryDate,
        UUID supplierPartnerId,
        LotStatus status,
        long version,
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        String updatedBy) {

    public static LotResult from(Lot lot) {
        return new LotResult(
                lot.getId(),
                lot.getSkuId(),
                lot.getLotNo(),
                lot.getManufacturedDate(),
                lot.getExpiryDate(),
                lot.getSupplierPartnerId(),
                lot.getStatus(),
                lot.getVersion(),
                lot.getCreatedAt(),
                lot.getCreatedBy(),
                lot.getUpdatedAt(),
                lot.getUpdatedBy());
    }
}
