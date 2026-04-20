package com.wms.master.application.command;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Create a Lot under a SKU. Cross-aggregate invariants (parent SKU ACTIVE and
 * {@code trackingType = LOT}) are enforced in {@code LotService.create}; the
 * domain factory handles intra-aggregate invariants.
 */
public record CreateLotCommand(
        UUID skuId,
        String lotNo,
        LocalDate manufacturedDate,
        LocalDate expiryDate,
        UUID supplierPartnerId,
        String actorId) {
}
