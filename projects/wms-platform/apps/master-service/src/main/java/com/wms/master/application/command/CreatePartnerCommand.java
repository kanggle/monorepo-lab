package com.wms.master.application.command;

import com.wms.master.domain.model.PartnerType;

public record CreatePartnerCommand(
        String partnerCode,
        String name,
        PartnerType partnerType,
        String businessNumber,
        String contactName,
        String contactEmail,
        String contactPhone,
        String address,
        String actorId) {
}
