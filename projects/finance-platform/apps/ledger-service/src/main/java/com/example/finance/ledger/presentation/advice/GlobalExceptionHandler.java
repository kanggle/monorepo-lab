package com.example.finance.ledger.presentation.advice;

import com.example.finance.ledger.domain.error.LedgerDomainException;
import com.example.finance.ledger.domain.money.Currency;
import com.example.finance.ledger.domain.money.Money;
import com.example.finance.ledger.presentation.dto.ApiErrorBody;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
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
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

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
            Map.entry("TENANT_FORBIDDEN", HttpStatus.FORBIDDEN));

    @ExceptionHandler(LedgerDomainException.class)
    public ResponseEntity<ApiErrorBody> handleDomain(LedgerDomainException e) {
        HttpStatus status = STATUS_BY_CODE.getOrDefault(e.code(),
                HttpStatus.UNPROCESSABLE_ENTITY);
        if (status.is5xxServerError()) {
            log.warn("domain failure {} -> {}: {}", e.code(), status, e.getMessage());
        }
        return ResponseEntity.status(status).body(ApiErrorBody.of(e.code(), e.getMessage()));
    }

    @ExceptionHandler(Currency.UnsupportedCurrencyException.class)
    public ResponseEntity<ApiErrorBody> handleUnsupportedCurrency(
            Currency.UnsupportedCurrencyException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiErrorBody.of("CURRENCY_MISMATCH", e.getMessage()));
    }

    @ExceptionHandler(Money.CurrencyMismatchException.class)
    public ResponseEntity<ApiErrorBody> handleMoneyCurrencyMismatch(
            Money.CurrencyMismatchException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiErrorBody.of("CURRENCY_MISMATCH", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorBody> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorBody.of("VALIDATION_ERROR", "Invalid parameter: " + e.getName()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorBody> handleMalformed(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorBody.of("VALIDATION_ERROR", "Malformed request body"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorBody> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiErrorBody.of("CURRENCY_MISMATCH", e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorBody> handleIllegalState(IllegalStateException e) {
        log.warn("illegal state at controller boundary", e);
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiErrorBody.of("VALIDATION_ERROR", e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorBody> handleGeneral(Exception e) {
        log.error("Unexpected error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorBody.of("INTERNAL_ERROR", "An unexpected error occurred"));
    }
}
