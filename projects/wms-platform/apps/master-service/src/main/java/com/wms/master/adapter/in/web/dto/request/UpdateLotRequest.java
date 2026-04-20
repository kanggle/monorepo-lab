package com.wms.master.adapter.in.web.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wms.master.application.command.UpdateLotCommand;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * PATCH body for a Lot update. Mutable: {@code expiryDate},
 * {@code supplierPartnerId}. Immutable fields ({@code skuId}, {@code lotNo},
 * {@code manufacturedDate}) are accepted on the wire so the domain can raise
 * {@code IMMUTABLE_FIELD} (422) rather than silently dropping them.
 *
 * <p>{@code clearSupplierPartnerId} lets callers explicitly null the link
 * (since a {@code null} JSON value for {@code supplierPartnerId} is
 * ambiguous — it could mean "no change" or "clear it"). Default false keeps
 * the common "absent = unchanged" semantics.
 */
public record UpdateLotRequest(
        LocalDate expiryDate,

        UUID supplierPartnerId,

        @JsonProperty("clearSupplierPartnerId")
        Boolean clearSupplierPartnerId,

        UUID skuId,

        @Size(max = 40, message = "lotNo must be <= 40 characters")
        String lotNo,

        LocalDate manufacturedDate,

        @NotNull(message = "version is required")
        @PositiveOrZero(message = "version must be >= 0")
        Long version) {

    public UpdateLotCommand toCommand(UUID id, String actorId) {
        boolean clear = clearSupplierPartnerId != null && clearSupplierPartnerId;
        return new UpdateLotCommand(
                id,
                expiryDate,
                supplierPartnerId,
                clear,
                skuId,
                lotNo,
                manufacturedDate,
                version,
                actorId);
    }
}
