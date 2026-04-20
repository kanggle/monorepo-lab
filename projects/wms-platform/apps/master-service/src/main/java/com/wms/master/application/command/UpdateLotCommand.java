package com.wms.master.application.command;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Update mutable fields on a Lot. Mutable: {@code expiryDate},
 * {@code supplierPartnerId}. Immutable and rejected if different:
 * {@code skuId}, {@code lotNo}, {@code manufacturedDate} (surfaces as
 * {@code IMMUTABLE_FIELD} → 422).
 *
 * <p>{@code clearSupplierPartnerId} lets callers explicitly null the field;
 * a null {@code supplierPartnerId} without the clear flag means "no change"
 * (matches the common "absent = unchanged" PATCH semantics elsewhere).
 *
 * @param skuIdAttempt            if non-null, rejected as immutable-field attempt
 * @param lotNoAttempt            if non-null and different, rejected similarly
 * @param manufacturedDateAttempt if non-null and different, rejected similarly
 * @param version                 optimistic-lock token from the caller
 */
public record UpdateLotCommand(
        UUID id,
        LocalDate expiryDate,
        UUID supplierPartnerId,
        boolean clearSupplierPartnerId,
        UUID skuIdAttempt,
        String lotNoAttempt,
        LocalDate manufacturedDateAttempt,
        long version,
        String actorId) {
}
