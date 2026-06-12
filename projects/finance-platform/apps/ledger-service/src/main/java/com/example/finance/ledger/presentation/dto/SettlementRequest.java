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
 */
public record SettlementRequest(
        String ledgerAccountCode,
        String currency,
        String settlementRate,
        String proceedsAccountCode,
        Instant postedAt,
        String reference,
        String memo) {

    /**
     * Map the validated request + the request-scoped fields (tenant, actor, idempotency
     * key) to the application command. The currency resolve surfaces an unsupported
     * currency as a 422 via the handler; {@code settlementRate} is parsed as an exact
     * {@link BigDecimal} (a non-numeric value surfaces as a 422).
     */
    public SettleForeignPositionCommand toCommand(String tenantId, String operatorSubject,
                                                  String idempotencyKey) {
        Currency resolved = Currency.of(currency);
        BigDecimal rate = parseRate(settlementRate);
        return new SettleForeignPositionCommand(
                tenantId, operatorSubject, ledgerAccountCode, resolved, rate,
                proceedsAccountCode, postedAt, reference, memo, idempotencyKey);
    }

    private static BigDecimal parseRate(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("settlementRate is required");
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("settlementRate must be a decimal string: " + value);
        }
    }
}
