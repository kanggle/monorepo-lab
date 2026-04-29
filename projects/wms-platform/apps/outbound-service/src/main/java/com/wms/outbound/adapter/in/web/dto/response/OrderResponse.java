package com.wms.outbound.adapter.in.web.dto.response;

import com.wms.outbound.application.result.OrderResult;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Wire shape of the {@code GET /orders/{id}}, {@code POST /orders}, and
 * {@code POST /orders/{id}:cancel} responses, per
 * {@code outbound-service-api.md} §1.1 and §1.4.
 *
 * <p>Cancel-specific fields ({@code previousStatus}, {@code cancelledReason},
 * {@code cancelledAt}, {@code cancelledBy}) are populated only by the cancel
 * endpoint; for create/query responses they serialise to {@code null}. The
 * Spring-Boot default Jackson {@code spring.jackson.default-property-inclusion}
 * may be set to {@code non_null} at the application level if those nulls are
 * undesirable on the wire.
 */
public record OrderResponse(
        UUID orderId,
        String orderNo,
        String source,
        UUID customerPartnerId,
        UUID warehouseId,
        LocalDate requiredShipDate,
        String notes,
        String status,
        List<OrderLineResponse> lines,
        UUID sagaId,
        String sagaState,
        long version,
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        String updatedBy,
        // §1.4 cancel response shape — null for non-cancel paths.
        String previousStatus,
        String cancelledReason,
        Instant cancelledAt,
        String cancelledBy
) {

    public static OrderResponse from(OrderResult r) {
        return new OrderResponse(
                r.orderId(),
                r.orderNo(),
                r.source(),
                r.customerPartnerId(),
                r.warehouseId(),
                r.requiredShipDate(),
                r.notes(),
                r.status(),
                r.lines() == null ? null : r.lines().stream().map(OrderLineResponse::from).toList(),
                r.sagaId(),
                r.sagaState(),
                r.version(),
                r.createdAt(),
                r.createdBy(),
                r.updatedAt(),
                r.updatedBy(),
                r.previousStatus(),
                r.cancelledReason(),
                r.cancelledAt(),
                r.cancelledBy());
    }
}
