package com.example.scmplatform.procurement.domain.asn;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * ASN (Advance Shipment Notice) line item — one supplier-shipped batch tied
 * to a specific {@code purchase_order_lines.id}. Owned by
 * {@link AdvanceShipmentNotice}.
 */
@Entity
@Table(name = "asn_lines")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AsnLine {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "asn_id", length = 36, nullable = false)
    private String asnId;

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Column(name = "po_line_id", length = 36, nullable = false)
    private String poLineId;

    @Column(name = "quantity_shipped", nullable = false, precision = 18, scale = 4)
    private BigDecimal quantityShipped;

    @Column(name = "quantity_received", precision = 18, scale = 4)
    private BigDecimal quantityReceived;

    public static AsnLine create(String id,
                                 String asnId,
                                 String tenantId,
                                 String poLineId,
                                 BigDecimal quantityShipped) {
        if (quantityShipped == null || quantityShipped.signum() <= 0) {
            throw new IllegalArgumentException("quantityShipped must be positive: " + quantityShipped);
        }
        AsnLine line = new AsnLine();
        line.id = id;
        line.asnId = asnId;
        line.tenantId = tenantId;
        line.poLineId = poLineId;
        line.quantityShipped = quantityShipped;
        return line;
    }

    public void markReceived() {
        this.quantityReceived = this.quantityShipped;
    }
}
