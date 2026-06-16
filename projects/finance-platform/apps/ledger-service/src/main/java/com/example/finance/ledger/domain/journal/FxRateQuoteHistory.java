package com.example.finance.ledger.domain.journal;

import com.example.finance.ledger.domain.money.Currency;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Append-only audit trail row for a fetched FX market-rate quote (26th increment —
 * TASK-FIN-BE-039, ADR-002 § 3.1 item 3 — {@code fx_rate_quote_history}). One row per poll
 * run per {@code (base_currency, foreign_currency)} pair; the surrogate {@code id} enables
 * multiple rows per pair (contrast: {@link FxRateQuote} has a composite PK = last-write-wins).
 *
 * <p>Mirrors {@link FxRateQuote}'s columns ({@code base_currency}, {@code foreign_currency},
 * {@code rate}, {@code as_of}, {@code source}, {@code fetched_at}) with a surrogate
 * {@link GenerationType#IDENTITY} PK. <b>Append-only</b>: no update / delete path exists —
 * the {@link com.example.finance.ledger.domain.journal.repository.FxRateQuoteHistoryRepository}
 * port exposes only an {@code append(…)} method (no findById, no deleteAll).
 *
 * <p>{@code rate} is an EXACT {@link BigDecimal} (base-minor-per-foreign-minor — the SAME unit
 * convention as {@code closingRate} / {@code settlementRate}, NOT a {@code BIGINT} minor amount).
 * {@code asOf} = the provider-stated rate instant; {@code fetchedAt} = when we pulled it;
 * {@code source} = the provider identifier (audit-heavy provenance). The currencies are stored
 * as their 3-letter ISO-4217 codes ({@link Currency#code()}).
 *
 * <p>NOT per-tenant — a market rate is tenant-agnostic. JPA annotations are the allowed
 * domain↔framework exception (exactly like {@link FxRateQuote} / {@code FxPositionLot}).
 */
@Entity
@Table(name = "fx_rate_quote_history")
@Getter
@Accessors(fluent = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FxRateQuoteHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "base_currency", length = 3, nullable = false)
    private String baseCurrency;

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

    private FxRateQuoteHistory(String baseCurrency, String foreignCurrency, BigDecimal rate,
                               Instant asOf, String source, Instant fetchedAt) {
        this.baseCurrency = baseCurrency;
        this.foreignCurrency = foreignCurrency;
        this.rate = rate;
        this.asOf = asOf;
        this.source = source;
        this.fetchedAt = fetchedAt;
    }

    /**
     * Build a history row for the given currency pair. The currencies are stored as their 3-letter
     * codes ({@link Currency#code()}) so the persisted form matches the {@code VARCHAR(3)} columns.
     */
    public static FxRateQuoteHistory of(Currency base, Currency foreign, BigDecimal rate,
                                        Instant asOf, String source, Instant fetchedAt) {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(foreign, "foreign");
        Objects.requireNonNull(rate, "rate");
        Objects.requireNonNull(asOf, "asOf");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(fetchedAt, "fetchedAt");
        return new FxRateQuoteHistory(base.code(), foreign.code(), rate, asOf, source, fetchedAt);
    }
}
