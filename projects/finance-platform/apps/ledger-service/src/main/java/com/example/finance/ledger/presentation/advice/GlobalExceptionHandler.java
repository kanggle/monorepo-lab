package com.example.finance.ledger.presentation.advice;

import com.example.finance.common.money.Currency;
import com.example.finance.common.money.Money;
import com.example.finance.ledger.domain.error.LedgerDomainException;
import com.example.web.dto.ErrorResponse;
import com.example.web.exception.CommonGlobalExceptionHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;

/**
 * Maps ledger domain exceptions to the ledger-api.md error envelope. The
 * authoritative code→HTTP table lives in ledger-api.md § Error codes;
 * {@link #STATUS_BY_CODE} is the exhaustive mechanical mirror — a single
 * {@link LedgerDomainException} handler resolves the status from the code so the
 * mapping cannot drift per-exception.
 *
 * <p><strong>ADR-MONO-058 § D2 (TASK-FIN-BE-066)</strong> — the generic, non-domain
 * tail (404 {@code NoResourceFound} / 404 {@code NoHandlerFound} / 405 + {@code Allow}
 * / 415 / the catch-all 500, plus the bean-validation, malformed-body,
 * missing-parameter, {@code IllegalArgumentException} and optimistic-lock arms) is now
 * inherited from {@link CommonGlobalExceptionHandler} instead of hand-copied here. The
 * envelope type is the shared {@link ErrorResponse} — finance's local
 * {@code ApiErrorBody} was retired because it carried a {@code details} field that no
 * arm in this service has ever populated (see the PR for the measurement), leaving it a
 * full duplicate of the shared record.
 *
 * <p>{@code validationFailureStatus()} is deliberately left at the base default
 * ({@code 400}) — ledger-api.md § Error codes registers {@code VALIDATION_ERROR} as
 * "always 400 in this service", which is exactly the base's behaviour.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends CommonGlobalExceptionHandler {

    /** ledger-api.md § Error codes (verbatim). */
    private static final Map<String, HttpStatus> STATUS_BY_CODE = Map.ofEntries(
            Map.entry("JOURNAL_ENTRY_NOT_FOUND", HttpStatus.NOT_FOUND),
            Map.entry("LEDGER_ACCOUNT_NOT_FOUND", HttpStatus.NOT_FOUND),
            Map.entry("LEDGER_ENTRY_UNBALANCED", HttpStatus.UNPROCESSABLE_ENTITY),
            Map.entry("CURRENCY_MISMATCH", HttpStatus.UNPROCESSABLE_ENTITY),
            Map.entry("LEDGER_PERIOD_CLOSED", HttpStatus.UNPROCESSABLE_ENTITY),
            Map.entry("ACCOUNTING_PERIOD_NOT_FOUND", HttpStatus.NOT_FOUND),
            Map.entry("ACCOUNTING_PERIOD_OVERLAP", HttpStatus.UNPROCESSABLE_ENTITY),
            Map.entry("ACCOUNTING_PERIOD_ALREADY_CLOSED", HttpStatus.CONFLICT),
            Map.entry("ACCOUNTING_PERIOD_INVALID_WINDOW", HttpStatus.UNPROCESSABLE_ENTITY),
            Map.entry("RECONCILIATION_ACCOUNT_INVALID", HttpStatus.UNPROCESSABLE_ENTITY),
            Map.entry("RECONCILIATION_STATEMENT_NOT_FOUND", HttpStatus.NOT_FOUND),
            Map.entry("RECONCILIATION_DISCREPANCY_NOT_FOUND", HttpStatus.NOT_FOUND),
            Map.entry("RECONCILIATION_ALREADY_RESOLVED", HttpStatus.CONFLICT),
            Map.entry("RECONCILIATION_PERIOD_LOCKED", HttpStatus.UNPROCESSABLE_ENTITY),
            Map.entry("IDEMPOTENCY_KEY_REQUIRED", HttpStatus.BAD_REQUEST),
            Map.entry("REVALUATION_RATE_INVALID", HttpStatus.UNPROCESSABLE_ENTITY),
            Map.entry("SETTLEMENT_RATE_INVALID", HttpStatus.UNPROCESSABLE_ENTITY),
            Map.entry("SETTLEMENT_AMOUNT_INVALID", HttpStatus.UNPROCESSABLE_ENTITY),
            Map.entry("FX_RATE_UNAVAILABLE", HttpStatus.UNPROCESSABLE_ENTITY),
            Map.entry("VALIDATION_ERROR", HttpStatus.BAD_REQUEST),
            // Emitted by the inherited optimistic-lock arm (ADR-MONO-058 § D2), not by a
            // LedgerDomainException — carried here so the table stays an exhaustive mirror
            // of ledger-api.md § Error codes.
            Map.entry("CONFLICT", HttpStatus.CONFLICT),
            Map.entry("ILLEGAL_STATE", HttpStatus.UNPROCESSABLE_ENTITY),
            Map.entry("TENANT_FORBIDDEN", HttpStatus.FORBIDDEN));

    /**
     * Resolve a code's status from {@link #STATUS_BY_CODE} — the single place a
     * code→status pair is decided. Handlers that <i>mint</i> a code (rather than
     * carry one on a {@link LedgerDomainException}) route through here too, so one
     * code can never leave this service at two different statuses.
     *
     * <p>The inherited catch-all is deliberately NOT routed here: {@code INTERNAL_ERROR}
     * is absent from the table, so the {@code getOrDefault} fallback would turn its
     * 500 into a 422.
     */
    private ResponseEntity<ErrorResponse> respond(String code, String message) {
        HttpStatus status = STATUS_BY_CODE.getOrDefault(code, HttpStatus.UNPROCESSABLE_ENTITY);
        return ResponseEntity.status(status).body(ErrorResponse.of(code, message));
    }

    @ExceptionHandler(LedgerDomainException.class)
    public ResponseEntity<ErrorResponse> handleDomain(LedgerDomainException e) {
        HttpStatus status = STATUS_BY_CODE.getOrDefault(e.code(),
                HttpStatus.UNPROCESSABLE_ENTITY);
        if (status.is5xxServerError()) {
            log.warn("domain failure {} -> {}: {}", e.code(), status, e.getMessage());
        }
        return ResponseEntity.status(status).body(ErrorResponse.of(e.code(), e.getMessage()));
    }

    @ExceptionHandler(Currency.UnsupportedCurrencyException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedCurrency(
            Currency.UnsupportedCurrencyException e) {
        return respond("CURRENCY_MISMATCH", e.getMessage());
    }

    @ExceptionHandler(Money.CurrencyMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMoneyCurrencyMismatch(
            Money.CurrencyMismatchException e) {
        return respond("CURRENCY_MISMATCH", e.getMessage());
    }

    /**
     * A required request header is absent — the manual posting {@code POST /entries}
     * without an {@code Idempotency-Key} (5th increment) → 400
     * {@code IDEMPOTENCY_KEY_REQUIRED} (ledger-api.md § 9; the blank/oversized-key
     * guard in the use case surfaces the same code via {@link LedgerDomainException}).
     * Overrides the base's generic 400 {@code VALIDATION_ERROR} arm, which would
     * publish a code this contract does not register for the case.
     */
    @Override
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException e) {
        return respond("IDEMPOTENCY_KEY_REQUIRED", "Missing required header: " + e.getHeaderName());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return respond("VALIDATION_ERROR", "Invalid parameter: " + e.getName());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException e) {
        log.warn("illegal state at controller boundary", e);
        return respond("ILLEGAL_STATE", e.getMessage());
    }
}
