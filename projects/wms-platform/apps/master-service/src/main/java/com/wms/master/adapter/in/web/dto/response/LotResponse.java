package com.wms.master.adapter.in.web.dto.response;

import com.wms.master.application.result.LotResult;
import com.wms.master.domain.model.LotStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record LotResponse(
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

    public static LotResponse from(LotResult result) {
        return new LotResponse(
                result.id(),
                result.skuId(),
                result.lotNo(),
                result.manufacturedDate(),
                result.expiryDate(),
                result.supplierPartnerId(),
                result.status(),
                result.version(),
                result.createdAt(),
                result.createdBy(),
                result.updatedAt(),
                result.updatedBy());
    }
}
