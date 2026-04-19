package com.wms.master.adapter.in.web.dto.request;

import com.wms.master.application.command.CreateSkuCommand;
import com.wms.master.domain.model.BaseUom;
import com.wms.master.domain.model.TrackingType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.Locale;

public record CreateSkuRequest(
        @NotBlank(message = "skuCode is required")
        @Size(min = 1, max = 40, message = "skuCode must be 1..40 characters")
        String skuCode,

        @NotBlank(message = "name is required")
        @Size(min = 1, max = 200, message = "name must be 1..200 characters")
        String name,

        @Size(max = 1000, message = "description must be <= 1000 characters")
        String description,

        @Size(max = 40, message = "barcode must be <= 40 characters")
        String barcode,

        @NotNull(message = "baseUom is required")
        BaseUom baseUom,

        @NotNull(message = "trackingType is required")
        TrackingType trackingType,

        @PositiveOrZero(message = "weightGrams must be >= 0")
        Integer weightGrams,

        @PositiveOrZero(message = "volumeMl must be >= 0")
        Integer volumeMl,

        @Size(max = 20, message = "hazardClass must be <= 20 characters")
        String hazardClass,

        @PositiveOrZero(message = "shelfLifeDays must be >= 0")
        Integer shelfLifeDays) {

    public CreateSkuCommand toCommand(String actorId) {
        // Uppercase the skuCode here for defence-in-depth; the domain factory
        // does it again. Keeps the wire-level value human-readable in the
        // command log while the DB stores the normalized form.
        String normalizedCode = skuCode == null ? null
                : skuCode.strip().toUpperCase(Locale.ROOT);
        return new CreateSkuCommand(
                normalizedCode,
                name,
                description,
                barcode,
                baseUom,
                trackingType,
                weightGrams,
                volumeMl,
                hazardClass,
                shelfLifeDays,
                actorId);
    }
}
