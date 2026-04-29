package com.wms.outbound.application.result;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Output record for {@code ReceiveOrderUseCase}, {@code CancelOrderUseCase},
 * and {@code QueryOrderUseCase.findById}. Mirrors the {@code OrderResponse}
 * shape declared in {@code outbound-service-api.md} §1.1 and §1.4.
 *
 * <p>The cancel-specific fields ({@code previousStatus}, {@code cancelledReason},
 * {@code cancelledAt}, {@code cancelledBy}) are populated only by
 * {@code CancelOrderService} per the §1.4 cancel response shape; they are
 * {@code null} for the create + query paths.
 */
public record OrderResult(
        UUID orderId,
        String orderNo,
        String source,
        UUID customerPartnerId,
        UUID warehouseId,
        LocalDate requiredShipDate,
        String notes,
        String status,
        long version,
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        String updatedBy,
        List<OrderLineResult> lines,
        UUID sagaId,
        String sagaState,
        // Cancel-specific fields (per outbound-service-api.md §1.4); null otherwise.
        String previousStatus,
        String cancelledReason,
        Instant cancelledAt,
        String cancelledBy
) {

    /**
     * Convenience constructor for paths that do not produce cancel metadata
     * (create + query). Cancel fields default to {@code null}.
     */
    public OrderResult(UUID orderId,
                       String orderNo,
                       String source,
                       UUID customerPartnerId,
                       UUID warehouseId,
                       LocalDate requiredShipDate,
                       String notes,
                       String status,
                       long version,
                       Instant createdAt,
                       String createdBy,
                       Instant updatedAt,
                       String updatedBy,
                       List<OrderLineResult> lines,
                       UUID sagaId,
                       String sagaState) {
        this(orderId, orderNo, source, customerPartnerId, warehouseId,
                requiredShipDate, notes, status, version,
                createdAt, createdBy, updatedAt, updatedBy,
                lines, sagaId, sagaState,
                null, null, null, null);
    }
}
