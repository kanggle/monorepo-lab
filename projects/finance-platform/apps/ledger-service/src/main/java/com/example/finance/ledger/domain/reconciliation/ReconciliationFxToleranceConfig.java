package com.example.finance.ledger.domain.reconciliation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.Objects;

/**
 * Per-tenant FX reconciliation tolerance config row (13th increment — TASK-FIN-BE-020,
 * architecture.md § FX reconciliation tolerance). One row per tenant ({@code tenant_id}
 * is the PK); <b>absence of a row means {@link FxTolerance#EXACT}</b> (no behaviour
 * change for existing tenants — net-zero). An operator upsert (last-write-wins) stamps
 * the audit fields ({@code updatedBy} / {@code updatedAt}) — regulated/audit-heavy.
 *
 * <p>JPA annotations are the allowed domain↔framework exception (exactly like
 * {@code AccountingPeriod} / {@code AuditLog}). The {@link #tolerance()} projection
 * exposes the pure {@link FxTolerance} the use case threads into the matcher.
 */
@Entity
@Table(name = "reconciliation_fx_tolerance")
@Getter
@Accessors(fluent = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReconciliationFxToleranceConfig {

    @Id
    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Column(name = "tolerance_bps", nullable = false)
    private int toleranceBps;

    @Column(name = "floor_minor", nullable = false)
    private long floorMinor;

    @Column(name = "updated_by", length = 64, nullable = false)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private ReconciliationFxToleranceConfig(String tenantId, int toleranceBps, long floorMinor,
                                            String updatedBy, Instant updatedAt) {
        this.tenantId = tenantId;
        this.toleranceBps = toleranceBps;
        this.floorMinor = floorMinor;
        this.updatedBy = updatedBy;
        this.updatedAt = updatedAt;
    }

    /**
     * Build a config row from a validated {@link FxTolerance} + audit identity. The
     * {@code FxTolerance} compact constructor (and the DB CHECKs) guarantee
     * {@code bps >= 0} / {@code floor >= 0}.
     */
    public static ReconciliationFxToleranceConfig of(String tenantId, FxTolerance tolerance,
                                                     String updatedBy, Instant updatedAt) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(tolerance, "tolerance");
        Objects.requireNonNull(updatedBy, "updatedBy");
        Objects.requireNonNull(updatedAt, "updatedAt");
        return new ReconciliationFxToleranceConfig(tenantId, tolerance.toleranceBps(),
                tolerance.absoluteFloorMinor(), updatedBy, updatedAt);
    }

    /** The pure tolerance value object the matcher consumes. */
    public FxTolerance tolerance() {
        return new FxTolerance(toleranceBps, floorMinor);
    }
}
