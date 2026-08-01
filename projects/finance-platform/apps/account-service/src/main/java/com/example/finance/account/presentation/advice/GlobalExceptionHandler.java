package com.example.finance.account.presentation.advice;

import com.example.common.persistence.DataIntegrityViolations;
import com.example.finance.account.domain.error.FinanceDomainException;
import com.example.finance.common.money.Currency;
import com.example.finance.common.money.Money;
import com.example.web.dto.ErrorResponse;
import com.example.web.exception.CommonGlobalExceptionHandler;
import jakarta.persistence.OptimisticLockException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;

/**
 * Maps fintech domain exceptions to the account-api.md error envelope. The
 * authoritative code→HTTP table lives in account-api.md § Error code → HTTP;
 * {@link #STATUS_BY_CODE} is the exhaustive mechanical mirror — a single
 * {@link FinanceDomainException} handler resolves the status from the code so
 * the mapping cannot drift per-exception.
 *
 * <p><strong>ADR-MONO-058 § D2 (TASK-FIN-BE-066)</strong> — the generic,
 * non-domain tail (404 {@code NoResourceFound} / 404 {@code NoHandlerFound} /
 * 405 + {@code Allow} / 415 / the catch-all 500, plus the bean-validation,
 * malformed-body and missing-parameter arms) is now inherited from
 * {@link CommonGlobalExceptionHandler} instead of hand-copied here. The envelope
 * type is the shared {@link ErrorResponse} — finance's local {@code ApiErrorBody}
 * was retired because it carried a {@code details} field that no arm in this
 * service has ever populated (see the PR for the measurement), leaving it a full
 * duplicate of the shared record.
 *
 * <p>Every arm below is here because its <em>code or status is finance's</em>,
 * not the fleet's. An arm that merely repeated the base verbatim was deleted;
 * an arm that diverges {@code @Override}s the base method of the same signature
 * (declaring a differently-named method for an exception type the base already
 * maps is an {@code Ambiguous @ExceptionHandler} failure at context startup, not
 * a compile error).
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends CommonGlobalExceptionHandler {

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
     * <p>The inherited catch-all is deliberately NOT routed here: {@code INTERNAL_ERROR}
     * is absent from the table, so the {@code getOrDefault} fallback would turn its
     * 500 into a 422.
     */
    private ResponseEntity<ErrorResponse> respond(String code, String message) {
        HttpStatus status = STATUS_BY_CODE.getOrDefault(code, HttpStatus.UNPROCESSABLE_ENTITY);
        return ResponseEntity.status(status).body(ErrorResponse.of(code, message));
    }

    @ExceptionHandler(FinanceDomainException.class)
    public ResponseEntity<ErrorResponse> handleDomain(FinanceDomainException e) {
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
     * Overrides the base's generic 400 {@code VALIDATION_ERROR} arm: on this service a
     * missing {@code Idempotency-Key} is the registered 400 {@code IDEMPOTENCY_KEY_REQUIRED}
     * (account-api.md), not an anonymous validation failure.
     */
    @Override
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException e) {
        if ("Idempotency-Key".equalsIgnoreCase(e.getHeaderName())) {
            return respond("IDEMPOTENCY_KEY_REQUIRED",
                    "Idempotency-Key header is required for mutating endpoints");
        }
        return respond("VALIDATION_ERROR", "Missing required header: " + e.getHeaderName());
    }

    /**
     * An unclassified bad argument reaching the controller boundary. Unlike
     * ledger-service — which keeps the base's {@code 400 VALIDATION_ERROR} — this
     * service overrides the arm to {@code 422 AMOUNT_INVALID}, and the asymmetry is
     * deliberate: {@code Money.of()} is the dominant IAE source here (negative minor
     * units, or a non-integer minor-unit string), and account-api.md registers exactly
     * that as {@code AMOUNT_INVALID | 422 | ≤0 / scale / minor-unit violation}.
     * Reclassifying it to 400 would break a documented behaviour. ledger has no
     * {@code AMOUNT_INVALID} code at all and its IAEs come from hand-parsed FX rate
     * strings, hence the split (TASK-MONO-348).
     *
     * <p>The base's {@code validationFailureStatus()} hook (ADR-MONO-058 § D2) is
     * deliberately NOT used for this: the hook moves the {@code @Valid} arm and this
     * arm together and keeps the code {@code VALIDATION_ERROR} for both, whereas this
     * service publishes <em>different codes at different statuses</em> for the two
     * (400 {@code VALIDATION_ERROR} for {@code @Valid}, 422 {@code AMOUNT_INVALID}
     * here). Overriding this single arm is the only way to express that without
     * changing either published behaviour (TASK-FIN-BE-066).
     */
    @Override
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        return respond("AMOUNT_INVALID", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException e) {
        return respond("VALIDATION_ERROR", "Invalid parameter: " + e.getName());
    }

    /**
     * Overrides the base's 409 {@code CONFLICT} arm — finance publishes the registered
     * descriptive alias {@code CONCURRENT_MODIFICATION} for the same optimistic-lock 409
     * (account-api.md; {@code platform/error-handling.md} § Transactional registers the
     * two as equivalent).
     */
    @Override
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(
            ObjectOptimisticLockingFailureException e) {
        return respond("CONCURRENT_MODIFICATION",
                "Concurrent modification detected. Please retry.");
    }

    /**
     * The JPA-native optimistic-lock failure, which the base does not map. Kept as a
     * separate overload rather than folded into a multi-type {@code @ExceptionHandler}:
     * a second method also claiming {@link ObjectOptimisticLockingFailureException}
     * would collide with the overridden arm above and fail context startup with
     * {@code Ambiguous @ExceptionHandler method mapped}.
     */
    @ExceptionHandler(OptimisticLockException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(OptimisticLockException e) {
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
     * turn the 500 into a 422), so the 500 is built directly, exactly like the inherited
     * catch-all.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleIntegrity(DataIntegrityViolationException e) {
        if (DataIntegrityViolations.isUniqueViolation(e)) {
            log.warn("unique constraint violation -> 409 CONCURRENT_MODIFICATION", e);
            return respond("CONCURRENT_MODIFICATION", "Data integrity conflict");
        }
        log.error("non-unique data integrity violation (FK / NOT NULL / CHECK) -> 500", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("INTERNAL_ERROR", "An unexpected error occurred"));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException e) {
        log.warn("illegal state at controller boundary", e);
        return respond("ILLEGAL_STATE", e.getMessage());
    }
}
