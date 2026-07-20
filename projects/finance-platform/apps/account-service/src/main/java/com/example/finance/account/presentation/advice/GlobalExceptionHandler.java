package com.example.finance.account.presentation.advice;

import com.example.common.persistence.DataIntegrityViolations;
import com.example.finance.account.domain.error.FinanceDomainException;
import com.example.finance.account.domain.money.Currency;
import com.example.finance.account.domain.money.Money;
import com.example.finance.account.presentation.dto.ApiErrorBody;
import jakarta.persistence.OptimisticLockException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;
import java.util.Set;

/**
 * Maps fintech domain exceptions to the account-api.md error envelope. The
 * authoritative code→HTTP table lives in account-api.md § Error code → HTTP;
 * {@link #STATUS_BY_CODE} is the exhaustive mechanical mirror — a single
 * {@link FinanceDomainException} handler resolves the status from the code so
 * the mapping cannot drift per-exception.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** account-api.md § Error code → HTTP status (verbatim). */
    private static final Map<String, HttpStatus> STATUS_BY_CODE = Map.ofEntries(
            Map.entry("VALIDATION_ERROR", HttpStatus.BAD_REQUEST),
            Map.entry("IDEMPOTENCY_KEY_REQUIRED", HttpStatus.BAD_REQUEST),
            Map.entry("IDEMPOTENCY_KEY_CONFLICT", HttpStatus.CONFLICT),
            Map.entry("ACCOUNT_NOT_FOUND", HttpStatus.NOT_FOUND),
            Map.entry("ACCOUNT_NOT_ACTIVE", HttpStatus.CONFLICT),
            Map.entry("ACCOUNT_FROZEN", HttpStatus.CONFLICT),
            Map.entry("ACCOUNT_STATUS_TRANSITION_INVALID", HttpStatus.CONFLICT),
            Map.entry("INSUFFICIENT_AVAILABLE_BALANCE", HttpStatus.UNPROCESSABLE_ENTITY),
            Map.entry("HOLD_NOT_FOUND", HttpStatus.NOT_FOUND),
            Map.entry("HOLD_ALREADY_SETTLED", HttpStatus.CONFLICT),
            Map.entry("TRANSACTION_NOT_FOUND", HttpStatus.NOT_FOUND),
            Map.entry("TRANSACTION_STATUS_TRANSITION_INVALID", HttpStatus.CONFLICT),
            Map.entry("TRANSACTION_ALREADY_SETTLED", HttpStatus.CONFLICT),
            Map.entry("CURRENCY_MISMATCH", HttpStatus.UNPROCESSABLE_ENTITY),
            Map.entry("AMOUNT_INVALID", HttpStatus.UNPROCESSABLE_ENTITY),
            Map.entry("KYC_REQUIRED", HttpStatus.FORBIDDEN),
            Map.entry("KYC_LEVEL_INSUFFICIENT", HttpStatus.FORBIDDEN),
            Map.entry("AML_SCREENING_REQUIRED", HttpStatus.UNPROCESSABLE_ENTITY),
            Map.entry("SANCTION_HIT", HttpStatus.UNPROCESSABLE_ENTITY),
            Map.entry("TRANSACTION_LIMIT_EXCEEDED", HttpStatus.UNPROCESSABLE_ENTITY),
            Map.entry("PERMISSION_DENIED", HttpStatus.FORBIDDEN),
            Map.entry("TENANT_FORBIDDEN", HttpStatus.FORBIDDEN),
            Map.entry("IDEMPOTENCY_STORE_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE),
            Map.entry("CONCURRENT_MODIFICATION", HttpStatus.CONFLICT),
            Map.entry("ILLEGAL_STATE", HttpStatus.UNPROCESSABLE_ENTITY));

    /**
     * Resolve a code's status from {@link #STATUS_BY_CODE} — the single place a
     * code→status pair is decided. Handlers that <i>mint</i> a code (rather than
     * carry one on a {@link FinanceDomainException}) route through here too, so one
     * code can never leave this service at two different statuses.
     *
     * <p>{@link #handleGeneral} is deliberately NOT routed here: {@code INTERNAL_ERROR}
     * is absent from the table, so the {@code getOrDefault} fallback would turn its
     * 500 into a 422.
     */
    private ResponseEntity<ApiErrorBody> respond(String code, String message) {
        HttpStatus status = STATUS_BY_CODE.getOrDefault(code, HttpStatus.UNPROCESSABLE_ENTITY);
        return ResponseEntity.status(status).body(ApiErrorBody.of(code, message));
    }

    @ExceptionHandler(FinanceDomainException.class)
    public ResponseEntity<ApiErrorBody> handleDomain(FinanceDomainException e) {
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
        return respond("CURRENCY_MISMATCH", e.getMessage());
    }

    @ExceptionHandler(Money.CurrencyMismatchException.class)
    public ResponseEntity<ApiErrorBody> handleMoneyCurrencyMismatch(
            Money.CurrencyMismatchException e) {
        return respond("CURRENCY_MISMATCH", e.getMessage());
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiErrorBody> handleMissingHeader(MissingRequestHeaderException e) {
        if ("Idempotency-Key".equalsIgnoreCase(e.getHeaderName())) {
            return respond("IDEMPOTENCY_KEY_REQUIRED",
                    "Idempotency-Key header is required for mutating endpoints");
        }
        return respond("VALIDATION_ERROR", "Missing required header: " + e.getHeaderName());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorBody> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .orElse("Validation failed");
        return respond("VALIDATION_ERROR", message);
    }

    /**
     * An unclassified bad argument reaching the controller boundary. Unlike
     * ledger-service — whose IAE catch-all answers {@code 400 VALIDATION_ERROR} —
     * this service keeps {@code 422 AMOUNT_INVALID}, and the asymmetry is deliberate:
     * {@code Money.of()} is the dominant IAE source here (negative minor units, or a
     * non-integer minor-unit string), and account-api.md registers exactly that as
     * {@code AMOUNT_INVALID | 422 | ≤0 / scale / minor-unit violation}. Reclassifying
     * it to 400 would break a documented behaviour. ledger has no {@code AMOUNT_INVALID}
     * code at all and its IAEs come from hand-parsed FX rate strings, hence the split
     * (TASK-MONO-348).
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorBody> handleIllegalArgument(IllegalArgumentException e) {
        return respond("AMOUNT_INVALID", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorBody> handleTypeMismatch(
            MethodArgumentTypeMismatchException e) {
        return respond("VALIDATION_ERROR", "Invalid parameter: " + e.getName());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorBody> handleMalformed(HttpMessageNotReadableException e) {
        return respond("VALIDATION_ERROR", "Malformed request body");
    }

    @ExceptionHandler({OptimisticLockException.class,
            ObjectOptimisticLockingFailureException.class})
    public ResponseEntity<ApiErrorBody> handleOptimisticLock(Exception e) {
        return respond("CONCURRENT_MODIFICATION",
                "Concurrent modification detected. Please retry.");
    }

    /**
     * DB constraint violation that no more specific handler claimed. Selective mapping
     * (TASK-MONO-450): a UNIQUE violation is a client-visible conflict → 409
     * {@code CONCURRENT_MODIFICATION} — this service's registered code for a duplicate /
     * concurrency conflict (account-api.md § Error code → HTTP). Finance deliberately uses
     * {@code CONCURRENT_MODIFICATION} here, NOT the fleet's {@code DATA_INTEGRITY_VIOLATION}
     * or {@code CONFLICT}; that is an intentional domain choice and is kept unchanged.
     *
     * <p>Every OTHER integrity violation (FK / NOT NULL / CHECK) is a SERVER defect, not a
     * client conflict, so it is deliberately surfaced as a loud 500 rather than hidden as a
     * 409 — mapping it to 409 would report a server bug as a client conflict and make the
     * defect disappear from alerting (TASK-MONO-450 § "왜 무조건 409 가 틀렸다"). This mirrors
     * ecommerce's TASK-BE-542 reference handler.
     *
     * <p>The non-unique arm cannot route through {@link #respond}: {@code INTERNAL_ERROR} is
     * deliberately absent from {@link #STATUS_BY_CODE} (its {@code getOrDefault} fallback would
     * turn the 500 into a 422), so the 500 is built directly, exactly like {@link #handleGeneral}.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorBody> handleIntegrity(DataIntegrityViolationException e) {
        if (DataIntegrityViolations.isUniqueViolation(e)) {
            log.warn("unique constraint violation -> 409 CONCURRENT_MODIFICATION", e);
            return respond("CONCURRENT_MODIFICATION", "Data integrity conflict");
        }
        log.error("non-unique data integrity violation (FK / NOT NULL / CHECK) -> 500", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorBody.of("INTERNAL_ERROR", "An unexpected error occurred"));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorBody> handleIllegalState(IllegalStateException e) {
        log.warn("illegal state at controller boundary", e);
        return respond("ILLEGAL_STATE", e.getMessage());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorBody> handleNoResourceFound(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorBody.of("NOT_FOUND", "The requested resource was not found"));
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiErrorBody> handleNoHandlerFound(NoHandlerFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorBody.of("NOT_FOUND", "The requested resource was not found"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorBody> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException e) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED);
        Set<HttpMethod> supported = e.getSupportedHttpMethods();
        if (supported != null && !supported.isEmpty()) {
            builder.allow(supported.toArray(new HttpMethod[0]));
        }
        return builder.body(ApiErrorBody.of("METHOD_NOT_ALLOWED",
                "HTTP method not supported for this endpoint"));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorBody> handleMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiErrorBody.of("UNSUPPORTED_MEDIA_TYPE",
                        "Request Content-Type is not supported by this endpoint"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorBody> handleGeneral(Exception e) {
        log.error("Unexpected error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorBody.of("INTERNAL_ERROR", "An unexpected error occurred"));
    }
}
