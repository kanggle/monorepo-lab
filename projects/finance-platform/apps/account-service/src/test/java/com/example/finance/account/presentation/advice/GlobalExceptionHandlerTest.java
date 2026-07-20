package com.example.finance.account.presentation.advice;

import com.example.finance.account.domain.error.DomainErrors;
import com.example.finance.account.domain.money.Currency;
import com.example.finance.account.domain.money.Money;
import com.example.finance.account.presentation.dto.ApiErrorBody;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GlobalExceptionHandler}. Asserts the
 * {@code (code → HTTP status)} contract is exactly account-api.md § Error
 * code → HTTP. Invokes the handler directly (fastest feedback for a mapper).
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private void assertStatus(ResponseEntity<ApiErrorBody> r,
                              HttpStatus status, String code) {
        assertThat(r.getStatusCode()).isEqualTo(status);
        assertThat(r.getBody()).isNotNull();
        assertThat(r.getBody().code()).isEqualTo(code);
        assertThat(r.getBody().timestamp()).isNotNull();
    }

    @Test
    @DisplayName("ACCOUNT_NOT_FOUND → 404")
    void accountNotFound() {
        assertStatus(handler.handleDomain(
                        new DomainErrors.AccountNotFoundException("nope")),
                HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND");
    }

    @Test
    @DisplayName("ACCOUNT_NOT_ACTIVE → 409, ACCOUNT_FROZEN → 409")
    void accountState() {
        assertStatus(handler.handleDomain(
                        new DomainErrors.AccountNotActiveException("x")),
                HttpStatus.CONFLICT, "ACCOUNT_NOT_ACTIVE");
        assertStatus(handler.handleDomain(
                        new DomainErrors.AccountFrozenException("x")),
                HttpStatus.CONFLICT, "ACCOUNT_FROZEN");
    }

    @Test
    @DisplayName("INSUFFICIENT_AVAILABLE_BALANCE → 422")
    void insufficient() {
        assertStatus(handler.handleDomain(
                        new DomainErrors.InsufficientAvailableBalanceException("x")),
                HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_AVAILABLE_BALANCE");
    }

    @Test
    @DisplayName("SANCTION_HIT → 422, AML_SCREENING_REQUIRED → 422")
    void compliance422() {
        assertStatus(handler.handleDomain(
                        new DomainErrors.SanctionHitException("x")),
                HttpStatus.UNPROCESSABLE_ENTITY, "SANCTION_HIT");
        assertStatus(handler.handleDomain(
                        new DomainErrors.AmlScreeningRequiredException("x")),
                HttpStatus.UNPROCESSABLE_ENTITY, "AML_SCREENING_REQUIRED");
    }

    @Test
    @DisplayName("KYC_REQUIRED → 403, KYC_LEVEL_INSUFFICIENT → 403, PERMISSION_DENIED → 403")
    void kyc403() {
        assertStatus(handler.handleDomain(
                        new DomainErrors.KycRequiredException("x")),
                HttpStatus.FORBIDDEN, "KYC_REQUIRED");
        assertStatus(handler.handleDomain(
                        new DomainErrors.KycLevelInsufficientException("x")),
                HttpStatus.FORBIDDEN, "KYC_LEVEL_INSUFFICIENT");
        assertStatus(handler.handleDomain(
                        new DomainErrors.PermissionDeniedException("x")),
                HttpStatus.FORBIDDEN, "PERMISSION_DENIED");
    }

    @Test
    @DisplayName("IDEMPOTENCY_KEY_CONFLICT → 409, IDEMPOTENCY_STORE_UNAVAILABLE → 503")
    void idempotency() {
        assertStatus(handler.handleDomain(
                        new DomainErrors.IdempotencyKeyConflictException("x")),
                HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_CONFLICT");
        assertStatus(handler.handleDomain(
                        new DomainErrors.IdempotencyStoreUnavailableException("x")),
                HttpStatus.SERVICE_UNAVAILABLE, "IDEMPOTENCY_STORE_UNAVAILABLE");
    }

    @Test
    @DisplayName("HOLD_NOT_FOUND → 404, HOLD_ALREADY_SETTLED → 409")
    void hold() {
        assertStatus(handler.handleDomain(
                        new DomainErrors.HoldNotFoundException("x")),
                HttpStatus.NOT_FOUND, "HOLD_NOT_FOUND");
        assertStatus(handler.handleDomain(
                        new DomainErrors.HoldAlreadySettledException("x")),
                HttpStatus.CONFLICT, "HOLD_ALREADY_SETTLED");
    }

    @Test
    @DisplayName("TRANSACTION_ALREADY_SETTLED → 409 (F3 reversal-only)")
    void txnSettled() {
        assertStatus(handler.handleDomain(
                        new DomainErrors.TransactionAlreadySettledException("x")),
                HttpStatus.CONFLICT, "TRANSACTION_ALREADY_SETTLED");
    }

    @Test
    @DisplayName("CURRENCY_MISMATCH → 422 (domain + Money + Currency variants)")
    void currencyMismatch() {
        assertStatus(handler.handleDomain(
                        new DomainErrors.CurrencyMismatchException("x")),
                HttpStatus.UNPROCESSABLE_ENTITY, "CURRENCY_MISMATCH");
        assertStatus(handler.handleMoneyCurrencyMismatch(
                        new Money.CurrencyMismatchException("x")),
                HttpStatus.UNPROCESSABLE_ENTITY, "CURRENCY_MISMATCH");
        assertStatus(handler.handleUnsupportedCurrency(
                        new Currency.UnsupportedCurrencyException("x")),
                HttpStatus.UNPROCESSABLE_ENTITY, "CURRENCY_MISMATCH");
    }

    @Test
    @DisplayName("generic Exception → 500 without leaking detail")
    void generic() {
        ResponseEntity<ApiErrorBody> r =
                handler.handleGeneral(new RuntimeException("secret crash"));
        assertStatus(r, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR");
        assertThat(r.getBody().message()).doesNotContain("secret crash");
    }

    // ---------------- TASK-MONO-348: the two previously untested handlers ----------------

    @Test
    @DisplayName("IllegalStateException → 422 ILLEGAL_STATE (never VALIDATION_ERROR)")
    void illegalStateUsesTheRegisteredCode() {
        ResponseEntity<ApiErrorBody> r = handler.handleIllegalState(
                new IllegalStateException("hold already released"));

        assertStatus(r, HttpStatus.UNPROCESSABLE_ENTITY, "ILLEGAL_STATE");
        assertThat(r.getBody().code())
                .as("VALIDATION_ERROR is registered at 400; emitting it at 422 splits the code")
                .isNotEqualTo("VALIDATION_ERROR");
    }

    /**
     * Deliberately asymmetric with ledger-service, whose IAE catch-all answers
     * {@code 400 VALIDATION_ERROR}. Here {@code Money.of()} is the dominant IAE source
     * (negative minor units / non-integer minor-unit string) and account-api.md registers
     * exactly that as {@code AMOUNT_INVALID | 422 | ≤0 / scale / minor-unit violation}, so
     * 422 AMOUNT_INVALID is the contract, not drift (TASK-MONO-348).
     */
    @Test
    @DisplayName("IllegalArgumentException → 422 AMOUNT_INVALID (Money is the IAE source here)")
    void illegalArgumentIsAnAmountViolation() {
        assertStatus(handler.handleIllegalArgument(
                        new IllegalArgumentException("amount must be non-negative minor units: -1")),
                HttpStatus.UNPROCESSABLE_ENTITY, "AMOUNT_INVALID");
    }

    /**
     * The invariant {@code STATUS_BY_CODE} exists to hold, asserted directly: a client that
     * branches on {@code code} must be able to rely on the status that comes with it. Revert
     * the {@code handleIllegalState} fix and {@code VALIDATION_ERROR} appears at both 400 and
     * 422 — this test fails.
     */
    @Test
    @DisplayName("no code leaves this service at two different HTTP statuses")
    void oneCodeOneStatus() {
        List<ResponseEntity<ApiErrorBody>> everyHandler = List.of(
                handler.handleIllegalArgument(new IllegalArgumentException("bad amount")),
                handler.handleIllegalState(new IllegalStateException("invariant")),
                handler.handleValidation(beanValidationFailure()),
                handler.handleMalformed(malformedBody()),
                handler.handleTypeMismatch(typeMismatch("accountId")),
                handler.handleMissingHeader(missingHeader("Idempotency-Key")),
                handler.handleMissingHeader(missingHeader("X-Custom")),
                handler.handleOptimisticLock(new OptimisticLockException("version stale")),
                handler.handleIntegrity(new DataIntegrityViolationException(
                        "duplicate key", new java.sql.SQLException("dup", "23505"))),
                handler.handleIntegrity(new DataIntegrityViolationException("fk violation")),
                handler.handleUnsupportedCurrency(new Currency.UnsupportedCurrencyException("XYZ")),
                handler.handleMoneyCurrencyMismatch(new Money.CurrencyMismatchException("KRW vs USD")),
                handler.handleDomain(new DomainErrors.AccountNotFoundException("nope")),
                handler.handleGeneral(new RuntimeException("boom")));

        Map<String, Set<HttpStatusCode>> statusesByCode = new LinkedHashMap<>();
        for (ResponseEntity<ApiErrorBody> r : everyHandler) {
            assertThat(r.getBody()).isNotNull();
            statusesByCode
                    .computeIfAbsent(r.getBody().code(), k -> new LinkedHashSet<>())
                    .add(r.getStatusCode());
        }

        assertThat(statusesByCode).isNotEmpty();
        assertThat(statusesByCode).allSatisfy((code, statuses) -> assertThat(statuses)
                .as("code %s is emitted at more than one status: %s", code, statuses)
                .hasSize(1));
    }

    // ---------------- TASK-MONO-450: selective DataIntegrityViolation mapping ----------------

    /**
     * A UNIQUE violation is the one client-visible integrity conflict. PostgreSQL / H2 signal it
     * with SQLSTATE 23505 (the shared {@code DataIntegrityViolations} discriminant). Kept at
     * 409 {@code CONCURRENT_MODIFICATION} — finance's registered duplicate/concurrency code,
     * deliberately not the fleet {@code DATA_INTEGRITY_VIOLATION}.
     */
    @Test
    @DisplayName("unique violation (SQLSTATE 23505) → 409 CONCURRENT_MODIFICATION")
    void uniqueViolationPostgresH2() {
        assertStatus(handler.handleIntegrity(new DataIntegrityViolationException(
                        "duplicate key value violates unique constraint",
                        new java.sql.SQLException("dup", "23505"))),
                HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION");
    }

    /**
     * This service runs on MySQL, which reports EVERY integrity class under SQLSTATE 23000 and
     * identifies a duplicate only by the vendor error code 1062 (ER_DUP_ENTRY). The shared 23505
     * check misses it, so the handler OR-s in a MySQL arm — without it a real MySQL duplicate
     * would wrongly fall through to 500. This is the load-bearing finance-specific case.
     */
    @Test
    @DisplayName("MySQL duplicate (SQLSTATE 23000, vendor code 1062) → 409 CONCURRENT_MODIFICATION")
    void uniqueViolationMysqlVendorCode() {
        java.sql.SQLException mysqlDup = new java.sql.SQLException("Duplicate entry", "23000", 1062);
        assertStatus(handler.handleIntegrity(new DataIntegrityViolationException(
                        "could not execute statement", mysqlDup)),
                HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION");
    }

    /**
     * FK / NOT NULL / CHECK violations are SERVER defects, not client conflicts. They stay a loud
     * 500 so they remain visible to alerting instead of being hidden as a 409 (TASK-MONO-450 §
     * "왜 무조건 409 가 틀렸다"). Covers a Postgres FK SQLSTATE (23503) and a MySQL NOT NULL vendor
     * code (1048), and the bare exception with no SQL cause.
     */
    @Test
    @DisplayName("FK violation (23503) → 500 INTERNAL_ERROR — server defect stays loud")
    void foreignKeyViolationStays500() {
        assertStatus(handler.handleIntegrity(new DataIntegrityViolationException(
                        "insert or update violates foreign key constraint",
                        new java.sql.SQLException("fk", "23503"))),
                HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR");
    }

    @Test
    @DisplayName("MySQL NOT NULL (vendor code 1048) → 500 INTERNAL_ERROR — server defect stays loud")
    void notNullViolationStays500() {
        java.sql.SQLException mysqlNotNull =
                new java.sql.SQLException("Column cannot be null", "23000", 1048);
        assertStatus(handler.handleIntegrity(new DataIntegrityViolationException(
                        "could not execute statement", mysqlNotNull)),
                HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR");
    }

    @Test
    @DisplayName("integrity violation with no SQL cause → 500 INTERNAL_ERROR (cannot prove unique)")
    void unclassifiableIntegrityStays500() {
        assertStatus(handler.handleIntegrity(new DataIntegrityViolationException("opaque")),
                HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR");
    }

    // ---------------- helpers ----------------

    private static HttpMessageNotReadableException malformedBody() {
        return new HttpMessageNotReadableException("malformed", (HttpInputMessage) null);
    }

    private static MethodArgumentTypeMismatchException typeMismatch(String name) {
        MethodArgumentTypeMismatchException ex = mock(MethodArgumentTypeMismatchException.class);
        when(ex.getName()).thenReturn(name);
        return ex;
    }

    private static MissingRequestHeaderException missingHeader(String name) {
        MissingRequestHeaderException ex = mock(MissingRequestHeaderException.class);
        when(ex.getHeaderName()).thenReturn(name);
        return ex;
    }

    private static MethodArgumentNotValidException beanValidationFailure() {
        BindingResult bindingResult = mock(BindingResult.class);
        when(bindingResult.getFieldErrors())
                .thenReturn(List.of(new FieldError("req", "amount", "must not be null")));
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);
        return ex;
    }
}
