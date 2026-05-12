package com.wms.master.adapter.in.web.dto.request;

import com.wms.master.application.command.CreatePartnerCommand;
import com.wms.master.domain.model.PartnerType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreatePartnerRequest(
        @NotBlank(message = "partnerCode is required")
        @Size(min = 1, max = 20, message = "partnerCode must be 1..20 characters")
        String partnerCode,

        @NotBlank(message = "name is required")
        @Size(min = 1, max = 200, message = "name must be 1..200 characters")
        String name,

        @NotNull(message = "partnerType is required")
        PartnerType partnerType,

        @Size(max = 20, message = "businessNumber must be <= 20 characters")
        String businessNumber,

        @Size(max = 100, message = "contactName must be <= 100 characters")
        String contactName,

        @Email(message = "contactEmail must be a valid email")
        @Size(max = 200, message = "contactEmail must be <= 200 characters")
        String contactEmail,

        @Size(max = 30, message = "contactPhone must be <= 30 characters")
        String contactPhone,

        @Size(max = 300, message = "address must be <= 300 characters")
        String address) {

    public CreatePartnerCommand toCommand(String actorId) {
        return new CreatePartnerCommand(
                partnerCode,
                name,
                partnerType,
                businessNumber,
                contactName,
                contactEmail,
                contactPhone,
                address,
                actorId);
    }
}
