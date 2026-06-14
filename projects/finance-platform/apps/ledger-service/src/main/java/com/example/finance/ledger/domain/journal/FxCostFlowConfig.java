package com.example.finance.ledger.domain.journal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.Objects;

/**
 * Per-tenant FX cost-flow method config row (15th increment — TASK-FIN-BE-023,
 * architecture.md § FX cost-flow method config). One row per tenant ({@code tenant_id}
 * is the PK); <b>absence of a row means {@link CostFlowMethod#WEIGHTED_AVERAGE}</b>
 * (no behaviour change for existing tenants — net-zero / shadow). An operator upsert
 * (last-write-wins) stamps the audit fields ({@code updatedBy} / {@code updatedAt}) —
 * regulated/audit-heavy.
 *
 * <p>JPA annotations are the allowed domain↔framework exception (exactly like
 * {@code ReconciliationFxToleranceConfig} / {@code AccountingPeriod} / {@code AuditLog}).
 * The {@link #method()} projection exposes the pure {@link CostFlowMethod} the use case
 * reads (FIN-BE-025 will branch on it to choose FIFO; in this increment the settlement
 * use case is not changed — shadow).
 */
@Entity
@Table(name = "fx_cost_flow_config")
@Getter
@Accessors(fluent = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FxCostFlowConfig {

    @Id
    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", length = 20, nullable = false)
    private CostFlowMethod method;

    @Column(name = "updated_by", length = 64, nullable = false)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private FxCostFlowConfig(String tenantId, CostFlowMethod method,
                             String updatedBy, Instant updatedAt) {
        this.tenantId = tenantId;
        this.method = method;
        this.updatedBy = updatedBy;
        this.updatedAt = updatedAt;
    }

    /**
     * Build a config row from a validated {@link CostFlowMethod} + audit identity. The
     * DB CHECK guarantees the stored string is in {WEIGHTED_AVERAGE, FIFO}.
     */
    public static FxCostFlowConfig of(String tenantId, CostFlowMethod method,
                                      String updatedBy, Instant updatedAt) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(updatedBy, "updatedBy");
        Objects.requireNonNull(updatedAt, "updatedAt");
        return new FxCostFlowConfig(tenantId, method, updatedBy, updatedAt);
    }
}
