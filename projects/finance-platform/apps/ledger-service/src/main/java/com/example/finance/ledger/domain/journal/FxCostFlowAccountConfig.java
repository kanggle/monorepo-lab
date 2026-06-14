package com.example.finance.ledger.domain.journal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.Objects;

/**
 * Per-ACCOUNT FX cost-flow method override row (21st increment — TASK-FIN-BE-029,
 * architecture.md § FX cost-flow method config / Per-account override). Generalises the
 * per-tenant {@link FxCostFlowConfig} (TASK-FIN-BE-023) to per ledger account: one row per
 * {@code (tenant_id, ledger_account_code)} composite key. <b>Absence of a row means the account
 * falls back to the per-tenant config, and if that is also absent, to
 * {@link CostFlowMethod#WEIGHTED_AVERAGE}</b> — no behaviour change for existing accounts
 * (net-zero). An operator upsert (last-write-wins) stamps the audit fields ({@code updatedBy} /
 * {@code updatedAt}) — regulated/audit-heavy.
 *
 * <p>The composite primary key is expressed via {@link IdClass} ({@link FxCostFlowAccountConfigId})
 * — the two {@code @Id} fields are {@code tenantId} + {@code ledgerAccountCode}. JPA annotations
 * are the allowed domain↔framework exception (exactly like {@link FxCostFlowConfig} /
 * {@code FxPositionLot} / {@code AuditLog}). The {@link #method()} projection exposes the pure
 * {@link CostFlowMethod} the settlement use case reads (it resolves the account override ahead of
 * the tenant default — see {@code SettleForeignPositionUseCase}).
 */
@Entity
@Table(name = "fx_cost_flow_account_config")
@IdClass(FxCostFlowAccountConfigId.class)
@Getter
@Accessors(fluent = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FxCostFlowAccountConfig {

    @Id
    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Id
    @Column(name = "ledger_account_code", length = 64, nullable = false)
    private String ledgerAccountCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", length = 20, nullable = false)
    private CostFlowMethod method;

    @Column(name = "updated_by", length = 64, nullable = false)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private FxCostFlowAccountConfig(String tenantId, String ledgerAccountCode, CostFlowMethod method,
                                    String updatedBy, Instant updatedAt) {
        this.tenantId = tenantId;
        this.ledgerAccountCode = ledgerAccountCode;
        this.method = method;
        this.updatedBy = updatedBy;
        this.updatedAt = updatedAt;
    }

    /**
     * Build an account override row from a validated {@link CostFlowMethod} + audit identity. The
     * DB CHECK guarantees the stored string is in {WEIGHTED_AVERAGE, FIFO}.
     */
    public static FxCostFlowAccountConfig of(String tenantId, String ledgerAccountCode,
                                             CostFlowMethod method, String updatedBy,
                                             Instant updatedAt) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(ledgerAccountCode, "ledgerAccountCode");
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(updatedBy, "updatedBy");
        Objects.requireNonNull(updatedAt, "updatedAt");
        return new FxCostFlowAccountConfig(tenantId, ledgerAccountCode, method, updatedBy, updatedAt);
    }
}
