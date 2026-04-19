package com.wms.master.application.command;

import com.wms.master.domain.model.BaseUom;
import com.wms.master.domain.model.TrackingType;
import java.util.UUID;

/**
 * Update mutable fields of an SKU. Absent (null) fields are left unchanged.
 *
 * @param skuCodeAttempt      if non-null, rejected as an immutable-field
 *                            attempt (surfaces as {@code IMMUTABLE_FIELD})
 * @param baseUomAttempt      if non-null and different from the stored value,
 *                            rejected the same way
 * @param trackingTypeAttempt if non-null and different from the stored value,
 *                            rejected the same way
 * @param version             caller-supplied optimistic-lock version; mismatch
 *                            against the stored version surfaces as
 *                            {@code CONFLICT}
 */
public record UpdateSkuCommand(
        UUID id,
        String name,
        String description,
        String barcode,
        Integer weightGrams,
        Integer volumeMl,
        String hazardClass,
        Integer shelfLifeDays,
        String skuCodeAttempt,
        BaseUom baseUomAttempt,
        TrackingType trackingTypeAttempt,
        long version,
        String actorId) {
}
