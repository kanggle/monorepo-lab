package com.wms.master.adapter.in.web.dto.request;

import com.wms.master.application.command.CreateLotCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Create-lot body. The {@code skuId} comes from the URL path (nested route —
 * {@code POST /api/v1/master/skus/{skuId}/lots}), not the body.
 *
 * <p>Cross-field date validation is enforced in the domain factory (defense
 * in depth), with a DB CHECK constraint {@code ck_lots_date_pair} as the
 * ultimate guard.
 */
public record CreateLotRequest(
        @NotBlank(message = "lotNo is required")
        @Size(min = 1, max = 40, message = "lotNo must be 1..40 characters")
        String lotNo,

        LocalDate manufacturedDate,

        LocalDate expiryDate,

        UUID supplierPartnerId) {

    public CreateLotCommand toCommand(UUID skuId, String actorId) {
        return new CreateLotCommand(
                skuId,
                lotNo,
                manufacturedDate,
                expiryDate,
                supplierPartnerId,
                actorId);
    }
}
