package com.wms.master.adapter.in.web.dto.request;

import com.wms.master.application.command.UpdatePartnerCommand;
import com.wms.master.domain.model.PartnerType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * PATCH body for a Partner update. Mutable fields: everything except
 * {@code partnerCode}. Caller may NOT change {@code partnerCode}; a non-null
 * value is rejected by the domain layer as {@code IMMUTABLE_FIELD} (422).
 * Exposed on the DTO so attempts are caught, not silently dropped by Jackson.
 */
public record UpdatePartnerRequest(
        @Size(min = 1, max = 200, message = "name must be 1..200 characters")
        String name,

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
        String address,

        // Surface partnerCode on the DTO so a client PATCH containing it is
        // captured (immutable-field reject, 422) rather than silently dropped.
        String partnerCode,

        @NotNull(message = "version is required")
        @PositiveOrZero(message = "version must be >= 0")
        Long version) {

    public UpdatePartnerCommand toCommand(UUID id, String actorId) {
        return new UpdatePartnerCommand(
                id,
                name,
                partnerType,
                businessNumber,
                contactName,
                contactEmail,
                contactPhone,
                address,
                partnerCode,
                version,
                actorId);
    }
}
