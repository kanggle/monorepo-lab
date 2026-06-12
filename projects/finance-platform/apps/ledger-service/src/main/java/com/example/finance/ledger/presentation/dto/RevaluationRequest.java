package com.example.finance.ledger.presentation.dto;

import com.example.finance.ledger.application.RevalueForeignBalanceCommand;
import com.example.finance.ledger.domain.money.Currency;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Request body for the FX revaluation endpoint (ledger-api.md § 10, 9th increment —
 * TASK-FIN-BE-015). {@code ledgerAccountCode} + {@code currency} identify the foreign
 * position; {@code closingRate} is the base-minor-per-foreign-minor spot factor as a
 * string decimal (never a float — F5). {@code postedAt} / {@code reference} /
 * {@code memo} are optional operator narrative.
 */
public record RevaluationRequest(
        String ledgerAccountCode,
        String currency,
        String closingRate,
        Instant postedAt,
        String reference,
        String memo) {

    /**
     * Map the validated request + the request-scoped fields (tenant, actor, idempotency
     * key) to the application command. The currency resolve surfaces an unsupported
     * currency as a 422 via the handler; {@code closingRate} is parsed as an exact
     * {@link BigDecimal} (a non-numeric value surfaces as a 422).
     */
    public RevalueForeignBalanceCommand toCommand(String tenantId, String operatorSubject,
                                                  String idempotencyKey) {
        Currency resolved = Currency.of(currency);
        BigDecimal rate = parseRate(closingRate);
        return new RevalueForeignBalanceCommand(
                tenantId, operatorSubject, ledgerAccountCode, resolved, rate,
                postedAt, reference, memo, idempotencyKey);
    }

    private static BigDecimal parseRate(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("closingRate is required");
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("closingRate must be a decimal string: " + value);
        }
    }
}
