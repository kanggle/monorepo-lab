package com.wms.outbound.application.result;

import java.util.UUID;

/**
 * Read-side result for a single {@code PickingRequestLine} within a
 * {@link PickingRequestResult}. Carries the planned location and quantity fields
 * that the §2.3 pick-confirmation body requires.
 *
 * <p>Populated by {@code PickingQueryService.toResult(PickingRequest)} from the
 * domain {@code PickingRequestLine} aggregate child.
 */
public record PickingRequestLineResult(
        UUID pickingRequestLineId,
        UUID orderLineId,
        UUID skuId,
        UUID lotId,
        UUID locationId,
        int qtyToPick
) {
}
