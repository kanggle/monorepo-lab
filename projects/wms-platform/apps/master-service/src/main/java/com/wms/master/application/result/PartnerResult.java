package com.wms.master.application.result;

import com.wms.master.domain.model.Partner;
import com.wms.master.domain.model.PartnerType;
import com.wms.master.domain.model.WarehouseStatus;
import java.time.Instant;
import java.util.UUID;

public record PartnerResult(
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

    public static PartnerResult from(Partner partner) {
        return new PartnerResult(
                partner.getId(),
                partner.getPartnerCode(),
                partner.getName(),
                partner.getPartnerType(),
                partner.getBusinessNumber(),
                partner.getContactName(),
                partner.getContactEmail(),
                partner.getContactPhone(),
                partner.getAddress(),
                partner.getStatus(),
                partner.getVersion(),
                partner.getCreatedAt(),
                partner.getCreatedBy(),
                partner.getUpdatedAt(),
                partner.getUpdatedBy());
    }
}
