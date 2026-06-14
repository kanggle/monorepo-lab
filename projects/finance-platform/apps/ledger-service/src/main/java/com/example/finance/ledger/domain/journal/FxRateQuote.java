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
 * Latest fetched FX market-rate quote for a currency pair (23rd increment — TASK-FIN-BE-031,
 * ADR-002 D2 — {@code fx_rate_quote} cache). One row per {@code (base_currency, foreign_currency)}
 * composite key; the scheduled poller ({@code FxRateFeedPoller} →
 * {@code RefreshFxRateQuotesUseCase}) upserts it last-write-wins. <b>Shadow / net-zero</b>: this
 * cache is loaded only; no operator path (settlement / revaluation) reads it in this increment.
 * FIN-BE-032 (D3/D4) wires the cache-fallback consumption + staleness guard.
 *
 * <p>{@code rate} is an EXACT {@link BigDecimal} (base-minor-per-foreign-minor — the SAME unit
 * convention as {@code closingRate} / {@code settlementRate}, NOT a {@code BIGINT} minor amount).
 * {@code asOf} = the provider-stated rate instant (staleness basis); {@code fetchedAt} = when we
 * pulled it; {@code source} = the provider identifier (audit-heavy provenance). The currencies are
 * stored as their 3-letter ISO-4217 codes ({@link Currency#code()}).
 *
 * <p>NOT per-tenant — a market rate is tenant-agnostic. JPA annotations are the allowed
 * domain↔framework exception (exactly like {@link FxCostFlowAccountConfig} / {@code FxPositionLot}).
 * The composite primary key is expressed via {@link IdClass} ({@link FxRateQuoteId}).
 */
@Entity
@Table(name = "fx_rate_quote")
@IdClass(FxRateQuoteId.class)
@Getter
@Accessors(fluent = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FxRateQuote {

    @Id
    @Column(name = "base_currency", length = 3, nullable = false)
    private String baseCurrency;

    @Id
    @Column(name = "foreign_currency", length = 3, nullable = false)
    private String foreignCurrency;

    @Column(name = "rate", nullable = false, precision = 20, scale = 8)
    private BigDecimal rate;

    @Column(name = "as_of", nullable = false)
    private Instant asOf;

    @Column(name = "source", length = 64, nullable = false)
    private String source;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    private FxRateQuote(String baseCurrency, String foreignCurrency, BigDecimal rate,
                        Instant asOf, String source, Instant fetchedAt) {
        this.baseCurrency = baseCurrency;
        this.foreignCurrency = foreignCurrency;
        this.rate = rate;
        this.asOf = asOf;
        this.source = source;
        this.fetchedAt = fetchedAt;
    }

    /**
     * Build a quote row for the given currency pair. The currencies are stored as their 3-letter
     * codes ({@link Currency#code()}) so the persisted form matches the {@code VARCHAR(3)} columns.
     */
    public static FxRateQuote of(Currency base, Currency foreign, BigDecimal rate,
                                 Instant asOf, String source, Instant fetchedAt) {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(foreign, "foreign");
        Objects.requireNonNull(rate, "rate");
        Objects.requireNonNull(asOf, "asOf");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(fetchedAt, "fetchedAt");
        return new FxRateQuote(base.code(), foreign.code(), rate, asOf, source, fetchedAt);
    }
}
