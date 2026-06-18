package com.example.finance.ledger.domain.journal;

import com.example.finance.ledger.domain.money.Currency;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Per-tenant FX contract-rate override row (28th increment — TASK-FIN-BE-042, ADR-002 § 3.1
 * deferred "per-tenant override / 특수 계약환율"). One row per
 * {@code (tenant_id, base_currency, foreign_currency)} composite key. <b>Absence of a row means
 * NO override</b> — FX resolution falls through to the existing tenant-agnostic feed path
 * byte-identically (net-zero / today's behaviour). When a row exists,
 * {@link com.example.finance.ledger.application.ResolveEffectiveFxRate} returns the contract
 * {@code rate} (with the audit source tag {@code override:contract}, {@code fromFeed=false}) ahead
 * of the market {@code fx_rate_quote} feed; an explicitly supplied manual rate still wins over the
 * override (most-specific operator intent). An operator upsert (last-write-wins) stamps the audit
 * fields ({@code updatedBy} / {@code updatedAt}) — regulated/audit-heavy.
 *
 * <p>{@code rate} is an EXACT {@link BigDecimal} (base-minor-per-foreign-minor — the SAME unit
 * convention as {@code FxRateQuote#rate()} / {@code closingRate} / {@code settlementRate}, NOT a
 * {@code BIGINT} minor amount); no {@code float}/{@code double} ever touches it (regulated F5). The
 * DB CHECK ({@code ck_fx_rate_override_rate_positive}) enforces {@code rate > 0} — the structural
 * backstop behind the application's {@code VALIDATION_ERROR} on a non-positive rate.
 *
 * <p>Tenant-scoped — {@code tenant_id} is part of the PK, so tenant A's override is invisible to
 * tenant B (the lookup is keyed by the caller's tenant). The composite primary key is expressed via
 * {@link IdClass} ({@link FxRateOverrideId}). JPA annotations are the allowed domain↔framework
 * exception (exactly like {@link FxRateQuote} / {@link FxCostFlowAccountConfig} /
 * {@code FxPositionLot} / {@code AuditLog}). The currencies are stored as their 3-letter ISO-4217
 * codes ({@link Currency#code()}).
 */
@Entity
@Table(name = "fx_rate_override")
@IdClass(FxRateOverrideId.class)
@Getter
@Accessors(fluent = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FxRateOverride {

    @Id
    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Id
    @Column(name = "base_currency", length = 3, nullable = false)
    private String baseCurrency;

    @Id
    @Column(name = "foreign_currency", length = 3, nullable = false)
    private String foreignCurrency;

    @Column(name = "rate", nullable = false, precision = 20, scale = 8)
    private BigDecimal rate;

    @Column(name = "updated_by", length = 64, nullable = false)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private FxRateOverride(String tenantId, String baseCurrency, String foreignCurrency,
                           BigDecimal rate, String updatedBy, Instant updatedAt) {
        this.tenantId = tenantId;
        this.baseCurrency = baseCurrency;
        this.foreignCurrency = foreignCurrency;
        this.rate = rate;
        this.updatedBy = updatedBy;
        this.updatedAt = updatedAt;
    }

    /**
     * Build an override row from a validated, strictly-positive contract {@code rate} + the
     * currency pair + audit identity. The {@code rate > 0} validation lives in the use case
     * (mapped to {@code VALIDATION_ERROR}); the DB CHECK is the structural backstop. The
     * currencies are stored as their ISO-4217 codes.
     */
    public static FxRateOverride of(String tenantId, Currency base, Currency foreign,
                                    BigDecimal rate, String updatedBy, Instant updatedAt) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(foreign, "foreign");
        Objects.requireNonNull(rate, "rate");
        Objects.requireNonNull(updatedBy, "updatedBy");
        Objects.requireNonNull(updatedAt, "updatedAt");
        return new FxRateOverride(tenantId, base.code(), foreign.code(), rate, updatedBy, updatedAt);
    }
}
