package com.example.finance.ledger.presentation.advice;

import com.example.finance.ledger.domain.error.LedgerErrors;
import com.example.finance.ledger.domain.money.Currency;
import com.example.finance.ledger.domain.money.Money;
import com.example.finance.ledger.presentation.dto.ApiErrorBody;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
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
 * Unit tests for {@link GlobalExceptionHandler}. Invokes each handler directly —
 * this class is a pure exception→envelope mapper, so Spring MVC buys nothing.
 *
 * <p>Asserts the {@code (code, HTTP status)} contract of ledger-api.md § Error codes.
 * The service shipped without this test, which is how the two defects fixed in
 * TASK-MONO-348 survived: the {@code IllegalArgumentException} /
 * {@code IllegalStateException} handlers were never exercised.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    // ---------------- the two TASK-MONO-348 defects ----------------

    /**
     * Every currency failure has a dedicated handler ({@code Currency.UnsupportedCurrencyException},
     * {@code Money.CurrencyMismatchException}, {@code LedgerErrors.CurrencyMismatchException}), so
     * nothing reaching the {@code IllegalArgumentException} catch-all is a currency mismatch. It
     * used to answer {@code 422 CURRENCY_MISMATCH} anyway — a code that contradicted its own
     * message and made FX dashboards over-count currency faults.
     */
    @Test
    @DisplayName("IllegalArgumentException → 400 VALIDATION_ERROR (never CURRENCY_MISMATCH)")
    void illegalArgumentIsNotACurrencyMismatch() {
        ResponseEntity<ApiErrorBody> r = handler.handleIllegalArgument(
                new IllegalArgumentException("settlementRate must be a decimal string: abc"));

        assertStatus(r, HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        assertThat(r.getBody().code())
                .as("a malformed rate string is not a currency mismatch")
                .isNotEqualTo("CURRENCY_MISMATCH");
        assertThat(r.getBody().message()).contains("settlementRate");
    }

    @Test
    @DisplayName("IllegalStateException → 422 ILLEGAL_STATE (never VALIDATION_ERROR)")
    void illegalStateUsesTheRegisteredCode() {
        ResponseEntity<ApiErrorBody> r = handler.handleIllegalState(
                new IllegalStateException("journal entry has no lines"));

        assertStatus(r, HttpStatus.UNPROCESSABLE_ENTITY, "ILLEGAL_STATE");
        assertThat(r.getBody().code())
                .as("VALIDATION_ERROR is registered at 400; emitting it at 422 splits the code")
                .isNotEqualTo("VALIDATION_ERROR");
    }

    /**
     * The invariant the handler's {@code STATUS_BY_CODE} table exists to hold, asserted
     * directly: a client that branches on {@code code} must be able to rely on the status
     * that comes with it. Both TASK-MONO-348 defects are exactly a violation of this —
     * revert either one and {@code VALIDATION_ERROR} appears at both 400 and 422.
     */
    @Test
    @DisplayName("no code leaves this service at two different HTTP statuses")
    void oneCodeOneStatus() {
        List<ResponseEntity<ApiErrorBody>> everyHandler = List.of(
                handler.handleIllegalArgument(new IllegalArgumentException("bad rate")),
                handler.handleIllegalState(new IllegalStateException("invariant")),
                handler.handleMalformed(malformedBody()),
                handler.handleTypeMismatch(typeMismatch("periodId")),
                handler.handleMissingHeader(missingHeader("Idempotency-Key")),
                handler.handleUnsupportedCurrency(new Currency.UnsupportedCurrencyException("XYZ")),
                handler.handleMoneyCurrencyMismatch(new Money.CurrencyMismatchException("KRW vs USD")),
                handler.handleDomain(new LedgerErrors.CurrencyMismatchException("mixed lines")),
                handler.handleDomain(new LedgerErrors.JournalEntryNotFoundException("nope")),
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

    // ---------------- CURRENCY_MISMATCH: the three legitimate sources ----------------

    @Test
    @DisplayName("CURRENCY_MISMATCH → 422 from the domain / Money / Currency handlers only")
    void currencyMismatchSources() {
        assertStatus(handler.handleDomain(new LedgerErrors.CurrencyMismatchException("mixed lines")),
                HttpStatus.UNPROCESSABLE_ENTITY, "CURRENCY_MISMATCH");
        assertStatus(handler.handleMoneyCurrencyMismatch(new Money.CurrencyMismatchException("KRW vs USD")),
                HttpStatus.UNPROCESSABLE_ENTITY, "CURRENCY_MISMATCH");
        assertStatus(handler.handleUnsupportedCurrency(new Currency.UnsupportedCurrencyException("XYZ")),
                HttpStatus.UNPROCESSABLE_ENTITY, "CURRENCY_MISMATCH");
    }

    // ---------------- VALIDATION_ERROR is always 400 ----------------

    @Test
    @DisplayName("every VALIDATION_ERROR path is 400 (malformed body, type mismatch, bad argument)")
    void validationErrorIsAlways400() {
        assertStatus(handler.handleMalformed(malformedBody()),
                HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        assertStatus(handler.handleTypeMismatch(typeMismatch("periodId")),
                HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        assertStatus(handler.handleIllegalArgument(new IllegalArgumentException("closingRate must be a decimal string: x")),
                HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
    }

    // ---------------- domain codes resolve through STATUS_BY_CODE ----------------

    @Test
    @DisplayName("domain codes resolve to their registered status")
    void domainCodes() {
        assertStatus(handler.handleDomain(new LedgerErrors.JournalEntryNotFoundException("nope")),
                HttpStatus.NOT_FOUND, "JOURNAL_ENTRY_NOT_FOUND");
        assertStatus(handler.handleDomain(new LedgerErrors.LedgerEntryUnbalancedException("debit != credit")),
                HttpStatus.UNPROCESSABLE_ENTITY, "LEDGER_ENTRY_UNBALANCED");
    }

    @Test
    @DisplayName("missing Idempotency-Key → 400 IDEMPOTENCY_KEY_REQUIRED")
    void missingIdempotencyHeader() {
        ResponseEntity<ApiErrorBody> r = handler.handleMissingHeader(missingHeader("Idempotency-Key"));
        assertStatus(r, HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED");
        assertThat(r.getBody().message()).contains("Idempotency-Key");
    }

    /**
     * {@code INTERNAL_ERROR} is absent from {@code STATUS_BY_CODE}, so routing this terminal
     * catch-all through the table's {@code getOrDefault} fallback would silently downgrade a
     * 500 to a 422. It stays hard-coded — this test is the tripwire.
     */
    @Test
    @DisplayName("generic Exception → 500 INTERNAL_ERROR without leaking detail")
    void generic() {
        ResponseEntity<ApiErrorBody> r = handler.handleGeneral(new RuntimeException("secret crash"));
        assertStatus(r, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR");
        assertThat(r.getBody().message()).doesNotContain("secret crash");
    }

    // ---------------- helpers ----------------

    private static void assertStatus(ResponseEntity<ApiErrorBody> r, HttpStatus expected, String code) {
        assertThat(r.getStatusCode()).isEqualTo(expected);
        assertThat(r.getBody()).isNotNull();
        assertThat(r.getBody().code()).isEqualTo(code);
        assertThat(r.getBody().timestamp()).isNotNull();
    }

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
}
