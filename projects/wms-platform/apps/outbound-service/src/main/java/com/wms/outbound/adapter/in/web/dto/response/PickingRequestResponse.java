package com.wms.outbound.adapter.in.web.dto.response;

import com.wms.outbound.application.result.PickingRequestResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Wire shape for a picking-request, per
 * {@code outbound-service-api.md} §2.1/§2.2/§2.4.
 *
 * <p>Used by the §2.4 list-by-order endpoint. The {@code lines} array carries
 * the planned {@code locationId} and {@code qtyToPick} each §2.3 confirmation
 * line requires.
 */
public record PickingRequestResponse(
        UUID pickingRequestId,
        UUID orderId,
        UUID sagaId,
        UUID warehouseId,
        String status,
        List<PickingRequestLineResponse> lines,
        long version,
        Instant createdAt,
        Instant updatedAt
) {

    public static PickingRequestResponse from(PickingRequestResult r) {
        List<PickingRequestLineResponse> lines = r.lines() == null
                ? List.of()
                : r.lines().stream().map(PickingRequestLineResponse::from).toList();
        return new PickingRequestResponse(
                r.pickingRequestId(),
                r.orderId(),
                r.sagaId(),
                r.warehouseId(),
                r.status(),
                lines,
                r.version(),
                r.createdAt(),
                r.updatedAt());
    }
}
