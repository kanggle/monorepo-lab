package com.wms.outbound.application.command;

import com.wms.outbound.domain.model.ShipToAddress;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Input record for {@code ReceiveOrderUseCase}. Carries the manual REST or
 * webhook-driven order data plus auth context (actorId + caller roles).
 *
 * <p>{@code shipTo} is an additive optional drop-ship recipient (ADR-MONO-022
 * D2-a) populated only by the {@code FULFILLMENT_ECOMMERCE} consumer path;
 * {@code null} for the manual-REST and ERP-webhook (B2B) paths.
 */
public record ReceiveOrderCommand(
        String orderNo,
        String source,
        UUID customerPartnerId,
        UUID warehouseId,
        LocalDate requiredShipDate,
        String notes,
        ShipToAddress shipTo,
        List<ReceiveOrderLineCommand> lines,
        String actorId,
        Set<String> callerRoles
) {

    /**
     * Backward-compatible constructor for B2B order intake (manual REST,
     * ERP webhook) — no drop-ship recipient ({@code shipTo == null}).
     */
    public ReceiveOrderCommand(String orderNo,
                               String source,
                               UUID customerPartnerId,
                               UUID warehouseId,
                               LocalDate requiredShipDate,
                               String notes,
                               List<ReceiveOrderLineCommand> lines,
                               String actorId,
                               Set<String> callerRoles) {
        this(orderNo, source, customerPartnerId, warehouseId, requiredShipDate,
                notes, null, lines, actorId, callerRoles);
    }
}
