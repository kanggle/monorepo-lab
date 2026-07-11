package com.example.finance.ledger.presentation.dto;

import com.example.finance.ledger.application.SettleForeignPositionCommand;
import com.example.finance.ledger.domain.money.Currency;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Request body for the FX settlement endpoint (ledger-api.md § 11, 10th increment —
 * TASK-FIN-BE-016). {@code ledgerAccountCode} + {@code currency} identify the foreign
 * position; {@code settlementRate} is the base-minor-per-foreign-minor spot factor as a
 * string decimal (never a float — F5); {@code proceedsAccountCode} is the base-currency
 * account that receives/pays the proceeds (must already exist). {@code postedAt} /
 * {@code reference} / {@code memo} are optional operator narrative.
 *
 * <p><b>12th increment (TASK-FIN-BE-018) — partial settlement.</b> The optional
 * {@code settleForeignAmount} (foreign minor string, F5) settles only a portion of the
 * position; omitting it settles the whole position byte-identically to the 10th
 * (net-zero). A non-integer value surfaces as {@code 400 VALIDATION_ERROR}; the
 * position-relative validation (zero / opposite-sign / over-settle -&gt;
 * {@code SETTLEMENT_AMOUNT_INVALID}, 422) is in the use case.
 *
 * <p><b>24th increment (TASK-FIN-BE-032, ADR-002 D3/D4) — {@code settlementRate} is now
 * OPTIONAL.</b> Omitting it (null/blank) resolves the rate from the FX rate feed cache when
 * the feed is enabled and a fresh quote exists; otherwise the use case fails closed with 422
 * {@code FX_RATE_UNAVAILABLE}. A supplied rate is used verbatim (net-zero). A non-numeric
 * supplied value surfaces as {@code 400 VALIDATION_ERROR} — it is a malformed request field,
 * no different from malformed JSON (TASK-MONO-348; it used to answer a nonsensical
 * {@code 422 CURRENCY_MISMATCH} via the handler's IAE catch-all).
 */
public record SettlementRequest(
        String ledgerAccountCode,
        String currency,
        String settlementRate,
        String proceedsAccountCode,
        String settleForeignAmount,
        Instant postedAt,
        String reference,
        String memo) {

    /**
     * Map the validated request + the request-scoped fields (tenant, actor, idempotency
     * key) to the application command. The currency resolve surfaces an unsupported
     * currency as a 422 via the handler; {@code settlementRate} is parsed as an exact
     * {@link BigDecimal} when present, or {@code null} when omitted (feed fallback in the
     * use case — 24th increment). A non-numeric value surfaces as {@code 400 VALIDATION_ERROR}.
     */
    public SettleForeignPositionCommand toCommand(String tenantId, String operatorSubject,
                                                  String idempotencyKey) {
        Currency resolved = Currency.of(currency);
        BigDecimal rate = parseRate(settlementRate);
        Long settleForeignMinor = parseSettleForeignAmount(settleForeignAmount);
        return new SettleForeignPositionCommand(
                tenantId, operatorSubject, ledgerAccountCode, resolved, rate,
                proceedsAccountCode, settleForeignMinor, postedAt, reference, memo, idempotencyKey);
    }

    private static BigDecimal parseRate(String value) {
        if (value == null || value.isBlank()) {
            return null; // omitted → feed fallback in the use case (24th increment — FIN-BE-032)
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("settlementRate must be a decimal string: " + value);
        }
    }

    private static Long parseSettleForeignAmount(String value) {
        if (value == null || value.isBlank()) {
            return null; // full settlement (net-zero, AC-2)
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "settleForeignAmount must be an integer minor-unit string: " + value);
        }
    }
}
