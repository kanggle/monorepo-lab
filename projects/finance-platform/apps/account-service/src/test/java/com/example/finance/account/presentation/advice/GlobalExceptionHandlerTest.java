package com.example.finance.account.presentation.advice;

import com.example.finance.account.domain.error.DomainErrors;
import com.example.finance.account.domain.money.Currency;
import com.example.finance.account.domain.money.Money;
import com.example.finance.account.presentation.dto.ApiErrorBody;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

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
}
