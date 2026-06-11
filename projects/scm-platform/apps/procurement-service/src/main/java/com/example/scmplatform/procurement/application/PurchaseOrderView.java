package com.example.scmplatform.procurement.application;

import com.example.scmplatform.procurement.domain.po.PoOrigin;
import com.example.scmplatform.procurement.domain.po.PurchaseOrder;
import com.example.scmplatform.procurement.domain.po.PurchaseOrderLine;
import com.example.scmplatform.procurement.domain.po.status.PoStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Read-model projection of a {@link PurchaseOrder} used by the controller
 * layer. Decouples presentation DTOs from the JPA-backed aggregate so
 * controller slice tests can build views without Hibernate.
 */
public record PurchaseOrderView(
        String id,
        String tenantId,
        String poNumber,
        String supplierId,
        String buyerAccountId,
        PoStatus status,
        PoOrigin origin,
        String sourceSuggestionId,
        BigDecimal totalAmount,
        String currency,
        Instant submittedAt,
        Instant acknowledgedAt,
        Instant confirmedAt,
        Instant canceledAt,
        Instant createdAt,
        Instant updatedAt,
        List<LineView> lines
) {

    public record LineView(
            String id,
            int lineNo,
            String sku,
            String supplierSku,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal receivedQuantity
    ) {
    }

    public static PurchaseOrderView from(PurchaseOrder po) {
        List<LineView> lines = po.linesView().stream().map(PurchaseOrderView::lineView).toList();
        return new PurchaseOrderView(
                po.getId(),
                po.getTenantId(),
                po.getPoNumber(),
                po.getSupplierId(),
                po.getBuyerAccountId(),
                po.getStatus(),
                po.getOrigin(),
                po.getSourceSuggestionId(),
                po.getTotalAmount().getAmount(),
                po.getTotalAmount().getCurrency(),
                po.getSubmittedAt(),
                po.getAcknowledgedAt(),
                po.getConfirmedAt(),
                po.getCanceledAt(),
                po.getCreatedAt(),
                po.getUpdatedAt(),
                lines
        );
    }

    private static LineView lineView(PurchaseOrderLine l) {
        return new LineView(
                l.getId(),
                l.getLineNo(),
                l.getSku(),
                l.getSupplierSku(),
                l.getQuantity(),
                l.getUnitPrice(),
                l.getReceivedQuantity()
        );
    }
}
