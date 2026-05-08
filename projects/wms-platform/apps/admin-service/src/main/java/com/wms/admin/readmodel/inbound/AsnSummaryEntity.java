package com.wms.admin.readmodel.inbound;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Projected from {@code wms.inbound.asn.received|cancelled|closed}. One row
 * per ASN. Per {@code domain-model.md § 6}.
 */
@Entity
@Table(name = "admin_asn_summary")
public class AsnSummaryEntity {

    @Id
    @Column(name = "asn_id")
    private UUID asnId;

    @Column(name = "asn_no", nullable = false, length = 80)
    private String asnNo;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Column(name = "supplier_partner_id")
    private UUID supplierPartnerId;

    @Column(name = "supplier_name", length = 200)
    private String supplierName;

    @Column(nullable = false, length = 40)
    private String status;

    @Column(length = 40)
    private String source;

    @Column(name = "expected_arrive_date")
    private LocalDate expectedArriveDate;

    @Column(name = "line_count", nullable = false)
    private int lineCount;

    @Column(name = "received_at")
    private Instant receivedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "last_event_at", nullable = false)
    private Instant lastEventAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected AsnSummaryEntity() {
    }

    public AsnSummaryEntity(UUID asnId, String asnNo, UUID warehouseId, UUID supplierPartnerId,
                            String supplierName, String status, String source,
                            LocalDate expectedArriveDate, int lineCount, Instant receivedAt,
                            Instant closedAt, Instant lastEventAt) {
        this.asnId = asnId;
        this.asnNo = asnNo;
        this.warehouseId = warehouseId;
        this.supplierPartnerId = supplierPartnerId;
        this.supplierName = supplierName;
        this.status = status;
        this.source = source;
        this.expectedArriveDate = expectedArriveDate;
        this.lineCount = lineCount;
        this.receivedAt = receivedAt;
        this.closedAt = closedAt;
        this.lastEventAt = lastEventAt;
    }

    public void applyReceived(String asnNo, UUID warehouseId, UUID supplierPartnerId,
                              String supplierName, String source, LocalDate expectedArriveDate,
                              int lineCount, Instant receivedAt, Instant lastEventAt) {
        this.asnNo = asnNo;
        this.warehouseId = warehouseId;
        this.supplierPartnerId = supplierPartnerId;
        this.supplierName = supplierName;
        this.source = source;
        this.expectedArriveDate = expectedArriveDate;
        this.lineCount = lineCount;
        this.receivedAt = receivedAt;
        this.status = "CREATED";
        this.lastEventAt = lastEventAt;
    }

    public void applyStatus(String status, Instant lastEventAt) {
        this.status = status;
        this.lastEventAt = lastEventAt;
    }

    public void applyClosed(Instant closedAt, Instant lastEventAt) {
        this.status = "CLOSED";
        this.closedAt = closedAt;
        this.lastEventAt = lastEventAt;
    }

    public UUID getAsnId() { return asnId; }
    public String getAsnNo() { return asnNo; }
    public UUID getWarehouseId() { return warehouseId; }
    public UUID getSupplierPartnerId() { return supplierPartnerId; }
    public String getSupplierName() { return supplierName; }
    public String getStatus() { return status; }
    public String getSource() { return source; }
    public LocalDate getExpectedArriveDate() { return expectedArriveDate; }
    public int getLineCount() { return lineCount; }
    public Instant getReceivedAt() { return receivedAt; }
    public Instant getClosedAt() { return closedAt; }
    public Instant getLastEventAt() { return lastEventAt; }
    public long getVersion() { return version; }
}
