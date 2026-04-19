package com.wms.master.adapter.in.web.dto.request;

import com.wms.master.application.command.UpdateSkuCommand;
import com.wms.master.domain.model.BaseUom;
import com.wms.master.domain.model.TrackingType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * PATCH body for an SKU update. Mutable fields: {@code name, description,
 * barcode, weightGrams, volumeMl, hazardClass, shelfLifeDays}. Caller may NOT
 * change {@code skuCode}, {@code baseUom}, {@code trackingType}; any non-null
 * value on those fields is rejected by the domain layer as
 * {@code IMMUTABLE_FIELD} (422). Exposed on the DTO so attempts are caught,
 * not silently dropped by Jackson.
 */
public record UpdateSkuRequest(
        @Size(min = 1, max = 200, message = "name must be 1..200 characters")
        String name,

        @Size(max = 1000, message = "description must be <= 1000 characters")
        String description,

        @Size(max = 40, message = "barcode must be <= 40 characters")
        String barcode,

        @PositiveOrZero(message = "weightGrams must be >= 0")
        Integer weightGrams,

        @PositiveOrZero(message = "volumeMl must be >= 0")
        Integer volumeMl,

        @Size(max = 20, message = "hazardClass must be <= 20 characters")
        String hazardClass,

        @PositiveOrZero(message = "shelfLifeDays must be >= 0")
        Integer shelfLifeDays,

        String skuCode,

        BaseUom baseUom,

        TrackingType trackingType,

        @NotNull(message = "version is required")
        @PositiveOrZero(message = "version must be >= 0")
        Long version) {

    public UpdateSkuCommand toCommand(UUID id, String actorId) {
        return new UpdateSkuCommand(
                id,
                name,
                description,
                barcode,
                weightGrams,
                volumeMl,
                hazardClass,
                shelfLifeDays,
                skuCode,
                baseUom,
                trackingType,
                version,
                actorId);
    }
}
