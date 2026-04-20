package com.wms.master.application.query;

import com.wms.master.domain.model.LotStatus;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Filter criteria for listing Lots.
 *
 * @param skuId        null = any SKU; concrete value scopes to that parent SKU
 * @param status       null = any; concrete value filters to that status
 * @param expiryBefore null = no upper bound; concrete value filters to
 *                     {@code expiry_date < expiryBefore}
 * @param expiryAfter  null = no lower bound; concrete value filters to
 *                     {@code expiry_date > expiryAfter}
 */
public record ListLotsCriteria(
        UUID skuId,
        LotStatus status,
        LocalDate expiryBefore,
        LocalDate expiryAfter) {

    public static ListLotsCriteria any() {
        return new ListLotsCriteria(null, null, null, null);
    }
}
