package com.example.scmplatform.procurement.domain.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * PurchaseOrder line item. Owned by {@link PurchaseOrder} aggregate root —
 * never edit a line directly. The aggregate enforces invariants:
 * <ul>
 *   <li>{@code line_no} is unique within a PO (DB UNIQUE constraint).</li>
 *   <li>{@code received_quantity} can never exceed {@code quantity}
 *       (enforced in {@link PurchaseOrder#applyAsnLine}).</li>
 * </ul>
 *
 * <p>Stored in its own JPA table {@code purchase_order_lines}; this is a
 * weak entity and is loaded explicitly with the parent rather than via
 * {@code @OneToMany} cascade so we avoid Hibernate's lazy-init footguns in
 * the hexagonal adapter layer.
 */
@Entity
@Table(name = "purchase_order_lines")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PurchaseOrderLine {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "po_id", length = 36, nullable = false)
    private String poId;

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Column(name = "sku", length = 100, nullable = false)
    private String sku;

    @Column(name = "supplier_sku", length = 100)
    private String supplierSku;

    @Column(name = "quantity", nullable = false, precision = 18, scale = 4)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 18, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "received_quantity", nullable = false, precision = 18, scale = 4)
    private BigDecimal receivedQuantity;

    public static PurchaseOrderLine create(String id,
                                           String poId,
                                           String tenantId,
                                           int lineNo,
                                           String sku,
                                           String supplierSku,
                                           BigDecimal quantity,
                                           BigDecimal unitPrice) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException("quantity must be positive: " + quantity);
        }
        if (unitPrice == null || unitPrice.signum() < 0) {
            throw new IllegalArgumentException("unitPrice must be non-negative: " + unitPrice);
        }
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("sku must not be blank");
        }
        PurchaseOrderLine line = new PurchaseOrderLine();
        line.id = id;
        line.poId = poId;
        line.tenantId = tenantId;
        line.lineNo = lineNo;
        line.sku = sku;
        line.supplierSku = supplierSku;
        line.quantity = quantity;
        line.unitPrice = unitPrice;
        line.receivedQuantity = BigDecimal.ZERO;
        return line;
    }

    public BigDecimal lineTotal() {
        return quantity.multiply(unitPrice);
    }

    public BigDecimal remainingQuantity() {
        return quantity.subtract(receivedQuantity);
    }

    public boolean isFullyReceived() {
        return receivedQuantity.compareTo(quantity) >= 0;
    }

    /**
     * Increase received quantity by {@code delta}. Throws when
     * {@code received + delta > quantity}. Used by ASN reconciliation.
     */
    public void addReceived(BigDecimal delta) {
        if (delta == null || delta.signum() <= 0) {
            throw new IllegalArgumentException("delta must be positive: " + delta);
        }
        BigDecimal next = this.receivedQuantity.add(delta);
        if (next.compareTo(this.quantity) > 0) {
            throw new IllegalArgumentException(
                    "ASN_OVERRECEIPT: received " + next + " > ordered " + this.quantity
                            + " for line " + this.id);
        }
        this.receivedQuantity = next;
    }
}
