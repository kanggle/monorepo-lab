package com.example.finance.ledger.domain.journal;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite primary-key class for {@link FxRateOverride} (28th increment — TASK-FIN-BE-042,
 * ADR-002 § 3.1 per-tenant override / 특수 계약환율). The {@code @IdClass} form requires a
 * {@link Serializable} class whose field names + types match the entity's {@code @Id} fields
 * ({@code tenantId} + {@code baseCurrency} + {@code foreignCurrency}, all {@code String} — the
 * tenant id + the 3-letter ISO-4217 codes), with a public no-arg constructor and value-based
 * {@link #equals(Object)} / {@link #hashCode()} (a plain class is the safe JPA {@code IdClass}
 * form — Lombok is avoided here so the no-arg ctor + equals/hashCode contract is explicit for the
 * JPA provider, mirroring {@link FxRateQuoteId} / {@link FxCostFlowAccountConfigId}).
 */
public class FxRateOverrideId implements Serializable {

    private static final long serialVersionUID = 1L;

    private String tenantId;
    private String baseCurrency;
    private String foreignCurrency;

    /** Required no-arg constructor for JPA. */
    public FxRateOverrideId() {
    }

    public FxRateOverrideId(String tenantId, String baseCurrency, String foreignCurrency) {
        this.tenantId = tenantId;
        this.baseCurrency = baseCurrency;
        this.foreignCurrency = foreignCurrency;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getBaseCurrency() {
        return baseCurrency;
    }

    public String getForeignCurrency() {
        return foreignCurrency;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FxRateOverrideId that)) {
            return false;
        }
        return Objects.equals(tenantId, that.tenantId)
                && Objects.equals(baseCurrency, that.baseCurrency)
                && Objects.equals(foreignCurrency, that.foreignCurrency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, baseCurrency, foreignCurrency);
    }
}
