package com.example.scmplatform.procurement.application;

import com.example.scmplatform.procurement.domain.po.PoOrigin;
import com.example.scmplatform.procurement.domain.po.PurchaseOrder;
import com.example.scmplatform.procurement.domain.po.PurchaseOrderLine;
import com.example.scmplatform.procurement.domain.po.status.PoStatus;
import com.example.scmplatform.procurement.domain.supplier.Supplier;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Read-model projection of a {@link PurchaseOrder} used by the controller
 * layer. Decouples presentation DTOs from the JPA-backed aggregate so
 * controller slice tests can build views without Hibernate.
 *
 * <p>{@code supplierCode} / {@code supplierName} (TASK-MONO-677) are the
 * supplier master row the stored {@code supplierId} resolves to — same tenant,
 * by id first, then by code — or {@code null} for both when nothing resolves.
 * They are never stored on the PO; see {@code procurement-api.md} § supplier
 * reference fields.
 */
public record PurchaseOrderView(
        String id,
        String tenantId,
        String poNumber,
        String supplierId,
        String supplierCode,
        String supplierName,
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

    /**
     * @param supplier the supplier master row {@code po.getSupplierId()} resolved
     *                 to inside the PO's tenant, or {@code null} when it did not
     *                 resolve — then both supplier fields are {@code null}
     *                 (never an empty string, never a copy of {@code supplierId}).
     */
    public static PurchaseOrderView from(PurchaseOrder po, Supplier supplier) {
        List<LineView> lines = po.linesView().stream().map(PurchaseOrderView::lineView).toList();
        return new PurchaseOrderView(
                po.getId(),
                po.getTenantId(),
                po.getPoNumber(),
                po.getSupplierId(),
                supplier == null ? null : supplier.getCode(),
                supplier == null ? null : supplier.getName(),
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
