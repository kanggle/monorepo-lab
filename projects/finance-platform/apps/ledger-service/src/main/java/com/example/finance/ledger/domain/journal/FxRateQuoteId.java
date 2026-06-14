package com.example.finance.ledger.domain.journal;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite primary-key class for {@link FxRateQuote} (23rd increment — TASK-FIN-BE-031,
 * ADR-002 D2). The {@code @IdClass} form requires a {@link Serializable} class whose field names +
 * types match the entity's {@code @Id} fields ({@code baseCurrency} + {@code foreignCurrency}, both
 * {@code String} — the 3-letter ISO-4217 code), with a public no-arg constructor and value-based
 * {@link #equals(Object)} / {@link #hashCode()} (a plain class is the safe JPA {@code IdClass} form —
 * Lombok is avoided here so the no-arg ctor + equals/hashCode contract is explicit for the JPA
 * provider, mirroring {@link FxCostFlowAccountConfigId}).
 */
public class FxRateQuoteId implements Serializable {

    private static final long serialVersionUID = 1L;

    private String baseCurrency;
    private String foreignCurrency;

    /** Required no-arg constructor for JPA. */
    public FxRateQuoteId() {
    }

    public FxRateQuoteId(String baseCurrency, String foreignCurrency) {
        this.baseCurrency = baseCurrency;
        this.foreignCurrency = foreignCurrency;
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
        if (!(o instanceof FxRateQuoteId that)) {
            return false;
        }
        return Objects.equals(baseCurrency, that.baseCurrency)
                && Objects.equals(foreignCurrency, that.foreignCurrency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(baseCurrency, foreignCurrency);
    }
}
