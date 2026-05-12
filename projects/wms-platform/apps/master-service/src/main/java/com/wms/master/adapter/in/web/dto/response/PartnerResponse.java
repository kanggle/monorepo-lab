package com.wms.master.adapter.in.web.dto.response;

import com.wms.master.application.result.PartnerResult;
import com.wms.master.domain.model.PartnerType;
import com.wms.master.domain.model.WarehouseStatus;
import java.time.Instant;
import java.util.UUID;

public record PartnerResponse(
        UUID id,
        String partnerCode,
        String name,
        PartnerType partnerType,
        String businessNumber,
        String contactName,
        String contactEmail,
        String contactPhone,
        String address,
        WarehouseStatus status,
        long version,
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        String updatedBy) {

    public static PartnerResponse from(PartnerResult result) {
        return new PartnerResponse(
                result.id(),
                result.partnerCode(),
                result.name(),
                result.partnerType(),
                result.businessNumber(),
                result.contactName(),
                result.contactEmail(),
                result.contactPhone(),
                result.address(),
                result.status(),
                result.version(),
                result.createdAt(),
                result.createdBy(),
                result.updatedAt(),
                result.updatedBy());
    }
}
