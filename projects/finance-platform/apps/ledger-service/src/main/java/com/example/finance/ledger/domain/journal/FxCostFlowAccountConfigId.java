package com.example.finance.ledger.domain.journal;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite primary-key class for {@link FxCostFlowAccountConfig} (21st increment —
 * TASK-FIN-BE-029). The {@code @IdClass} form requires a {@link Serializable} class whose field
 * names + types match the entity's {@code @Id} fields ({@code tenantId} + {@code ledgerAccountCode},
 * both {@code String}), with a public no-arg constructor and value-based {@link #equals(Object)} /
 * {@link #hashCode()} (a plain class is the safe JPA {@code IdClass} form — Lombok is avoided here
 * so the no-arg ctor + equals/hashCode contract is explicit and unambiguous for the JPA provider).
 */
public class FxCostFlowAccountConfigId implements Serializable {

    private static final long serialVersionUID = 1L;

    private String tenantId;
    private String ledgerAccountCode;

    /** Required no-arg constructor for JPA. */
    public FxCostFlowAccountConfigId() {
    }

    public FxCostFlowAccountConfigId(String tenantId, String ledgerAccountCode) {
        this.tenantId = tenantId;
        this.ledgerAccountCode = ledgerAccountCode;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getLedgerAccountCode() {
        return ledgerAccountCode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FxCostFlowAccountConfigId that)) {
            return false;
        }
        return Objects.equals(tenantId, that.tenantId)
                && Objects.equals(ledgerAccountCode, that.ledgerAccountCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, ledgerAccountCode);
    }
}
