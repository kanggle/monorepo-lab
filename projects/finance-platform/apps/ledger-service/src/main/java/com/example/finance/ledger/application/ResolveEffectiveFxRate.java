package com.example.finance.ledger.application;

import com.example.finance.ledger.application.port.outbound.ClockPort;
import com.example.finance.ledger.application.port.outbound.FxRateFeedSettings;
import com.example.finance.ledger.domain.error.LedgerErrors.FxRateUnavailableException;
import com.example.finance.ledger.domain.journal.FxRateOverride;
import com.example.finance.ledger.domain.journal.FxRateQuote;
import com.example.finance.ledger.domain.journal.repository.FxRateOverrideRepository;
import com.example.finance.ledger.domain.journal.repository.FxRateQuoteRepository;
import com.example.finance.ledger.domain.money.Currency;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Resolve the effective FX rate for an operator revaluation/settlement (24th increment —
 * TASK-FIN-BE-032, ADR-002 D3/D4 — the first operator-path consumer of the FIN-BE-031 cache).
 *
 * <p><b>Precedence (most-specific operator intent wins; the per-tenant contract overrides the
 * market feed; the feed fills the gap) — 28th increment (TASK-FIN-BE-042, ADR-002 § 3.1
 * per-tenant override / 특수 계약환율):</b>
 * <ol>
 *   <li>the operator <b>supplied</b> a rate → {@link ResolvedFxRate#fromFeed()} {@code = false},
 *       {@code rate} = exactly the supplied value, {@code sourceDescription = "manual"} — neither
 *       the override nor the cache is consulted (<b>net-zero</b>: the provided-rate path is
 *       byte-identical to before this increment);</li>
 *   <li>the rate is <b>omitted</b> ({@code null}) and a per-tenant <b>contract override</b> exists
 *       for the {@code (tenant, base, foreign)} pair → {@code fromFeed = false},
 *       {@code rate} = the contract rate, {@code sourceDescription = "override:contract"} (audit
 *       provenance) — the override shadows the market feed (a tenant's negotiated rate takes
 *       precedence over the public market rate). Tenant-scoped: tenant A's override never applies
 *       to tenant B (the lookup is keyed by the caller's tenant);</li>
 *   <li>the rate is <b>omitted</b> and <b>no</b> override row exists — the feed must supply it
 *       (regulated fail-closed; an estimated/stale rate must never recognise P&amp;L) —
 *       byte-identical to before this increment (<b>net-zero</b> when no contract rate is set):
 *       <ul>
 *         <li>feed <b>disabled</b> → {@link FxRateUnavailableException};</li>
 *         <li>no cached quote for the pair → {@link FxRateUnavailableException};</li>
 *         <li>cached quote is <b>stale</b> ({@code now − quote.asOf > staleAfter}) →
 *             {@link FxRateUnavailableException};</li>
 *         <li>otherwise {@code fromFeed = true}, {@code rate} = the cached quote's rate,
 *             {@code sourceDescription = "feed:" + source + "@" + asOf} (audit provenance).</li>
 *       </ul>
 *   </li>
 * </ol>
 *
 * <p>Read-only: it never writes (the idempotency key / dedupe row stay the use case's concern —
 * a rejection leaves nothing persisted, AC-3). The {@code quote.rate > 0} validation stays in
 * {@code FxSettlementPolicy} / {@code FxRevaluationPolicy} (this resolver only supplies a value;
 * a cached/override rate ≤ 0 still surfaces SETTLEMENT/REVALUATION_RATE_INVALID downstream, edge
 * case — though the override upsert + the DB CHECK already reject a non-positive contract rate).
 */
@Service
@RequiredArgsConstructor
public class ResolveEffectiveFxRate {

    /** Audit source tag for a per-tenant contract-rate override (28th increment). */
    static final String OVERRIDE_SOURCE = "override:contract";

    private final FxRateQuoteRepository fxRateQuoteRepository;
    private final FxRateOverrideRepository fxRateOverrideRepository;
    private final FxRateFeedSettings settings;
    private final ClockPort clock;

    /**
     * The resolved rate plus its provenance. {@code fromFeed=false} = either the operator's manual
     * rate ({@code sourceDescription = "manual"}) or a per-tenant contract override
     * ({@code sourceDescription = "override:contract"}); {@code fromFeed=true} = a fresh cached
     * quote ({@code sourceDescription = "feed:<source>@<asOf>"}) — recorded in the audit reason for
     * traceability (AC-2 / regulated-audit: an operator can see WHY a given rate was applied).
     */
    public record ResolvedFxRate(BigDecimal rate, boolean fromFeed, String sourceDescription) {
    }

    /**
     * Resolve the rate for the {@code (base, foreign)} pair under the caller's {@code tenantId},
     * preferring the operator-supplied {@code providedRate}, then the per-tenant contract override,
     * then the fresh cached market quote.
     *
     * @param tenantId     the caller's tenant (override lookup is tenant-scoped — AC-3)
     * @param base         the reporting/base currency (KRW)
     * @param foreign      the position's foreign currency
     * @param providedRate the operator-supplied rate, or {@code null} to fall back to the
     *                     override / feed
     * @return the resolved rate + provenance
     * @throws FxRateUnavailableException when the rate is omitted, no override exists, and no fresh
     *                                    quote can supply it (feed disabled / no quote / stale) —
     *                                    422, nothing persists
     */
    public ResolvedFxRate resolve(String tenantId, Currency base, Currency foreign,
                                  BigDecimal providedRate) {
        // (1) Manual operator-supplied rate — the most specific intent. Net-zero (byte-identical):
        //     neither the override nor the cache is consulted.
        if (providedRate != null) {
            return new ResolvedFxRate(providedRate, false, "manual");
        }
        // (2) Per-tenant contract override (special contract rate) — overrides the market feed.
        //     Tenant-scoped: keyed by the caller's tenant, so tenant A's row never applies to
        //     tenant B (AC-3). Absent → fall through to the feed UNCHANGED (net-zero, AC-2).
        Optional<FxRateOverride> override = fxRateOverrideRepository.findOverride(tenantId, base, foreign);
        if (override.isPresent()) {
            return new ResolvedFxRate(override.get().rate(), false, OVERRIDE_SOURCE);
        }
        // (3) Market feed fallback — byte-identical to before this increment when no override row
        //     exists (the primary, net-zero path).
        if (!settings.feedEnabled()) {
            throw new FxRateUnavailableException(
                    "no FX rate supplied and the FX rate feed is disabled — supply a manual rate");
        }
        FxRateQuote quote = fxRateQuoteRepository.findLatest(base, foreign)
                .orElseThrow(() -> new FxRateUnavailableException(
                        "no cached FX rate quote for " + base.code() + "/" + foreign.code()
                                + " — supply a manual rate"));
        Instant now = clock.now();
        // now − asOf > staleAfter → stale. Exactly == staleAfter is FRESH (inclusive, AC-4).
        if (Duration.between(quote.asOf(), now).compareTo(settings.staleAfter()) > 0) {
            throw new FxRateUnavailableException(
                    "cached FX rate quote for " + base.code() + "/" + foreign.code()
                            + " is stale (as_of " + quote.asOf() + ") — supply a manual rate");
        }
        return new ResolvedFxRate(quote.rate(), true,
                "feed:" + quote.source() + "@" + quote.asOf());
    }
}
