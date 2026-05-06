package com.example.scmplatform.procurement.domain.asn;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * ASN (Advance Shipment Notice) aggregate root.
 *
 * <p>Idempotency invariant (S2): the tuple
 * {@code (tenant_id, supplier_asn_ref)} is unique. The DB enforces this with
 * a UNIQUE constraint; the application layer relies on it to detect replay /
 * webhook retries and respond with the previously-stored result.
 */
@Entity
@Table(name = "advance_shipment_notices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdvanceShipmentNotice {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Column(name = "po_id", length = 36, nullable = false)
    private String poId;

    @Column(name = "supplier_asn_ref", length = 100, nullable = false)
    private String supplierAsnRef;

    @Column(name = "expected_arrival_at", nullable = false)
    private Instant expectedArrivalAt;

    @Column(name = "received_at")
    private Instant receivedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Transient
    private final List<AsnLine> lines = new ArrayList<>();

    public static AdvanceShipmentNotice create(String id,
                                               String tenantId,
                                               String poId,
                                               String supplierAsnRef,
                                               Instant expectedArrivalAt) {
        AdvanceShipmentNotice asn = new AdvanceShipmentNotice();
        asn.id = id;
        asn.tenantId = tenantId;
        asn.poId = poId;
        asn.supplierAsnRef = supplierAsnRef;
        asn.expectedArrivalAt = expectedArrivalAt;
        asn.createdAt = Instant.now();
        return asn;
    }

    public void addLine(AsnLine line) {
        this.lines.add(line);
    }

    public void hydrateLines(List<AsnLine> hydrated) {
        this.lines.clear();
        this.lines.addAll(hydrated);
    }

    public List<AsnLine> linesView() {
        return List.copyOf(this.lines);
    }

    public void markReceivedNow() {
        this.receivedAt = Instant.now();
        for (AsnLine line : this.lines) {
            line.markReceived();
        }
    }
}
