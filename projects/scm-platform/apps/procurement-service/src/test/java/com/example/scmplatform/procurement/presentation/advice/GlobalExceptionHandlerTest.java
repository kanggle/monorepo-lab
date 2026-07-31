package com.example.scmplatform.procurement.presentation.advice;

import com.example.scmplatform.procurement.domain.error.AsnOverreceiptException;
import com.example.scmplatform.procurement.domain.error.CatalogSkuUnknownException;
import com.example.scmplatform.procurement.domain.error.IdempotencyKeyMismatchException;
import com.example.scmplatform.procurement.domain.error.PoAlreadyConfirmedException;
import com.example.scmplatform.procurement.domain.error.PoNotFoundException;
import com.example.scmplatform.procurement.domain.error.PoQuantityExceededException;
import com.example.scmplatform.procurement.domain.error.PoStatusTransitionInvalidException;
import com.example.scmplatform.procurement.domain.error.SupplierInactiveException;
import com.example.scmplatform.procurement.domain.error.SupplierNotFoundException;
import com.example.scmplatform.procurement.domain.error.SupplierUnavailableException;
import com.example.scmplatform.procurement.domain.po.status.ActorType;
import com.example.scmplatform.procurement.domain.po.status.PoStatus;
import com.example.scmplatform.procurement.presentation.dto.ApiErrorBody;
import com.example.web.dto.ErrorResponse;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GlobalExceptionHandler}. Bypasses Spring MVC entirely
 * and invokes each handler method directly — fastest feedback for a pure
 * mapper that only translates exceptions to error envelopes.
 *
 * <p>Since TASK-SCM-BE-055 (ADR-MONO-058 D2) most arms return the shared
 * {@link ErrorResponse}; only {@code PO_STATUS_TRANSITION_INVALID} returns the
 * {@code details}-carrying {@link ApiErrorBody} extension. The generic tail
 * (404/405/415/malformed-body/catch-all) is inherited from
 * {@code CommonGlobalExceptionHandler} — its real registration is proven by
 * {@code GlobalExceptionHandlerNotFoundTest}, which drives Spring's resolver.
 *
 * <p>Asserts the {@code (HttpStatus, code)} contract documented in
 * {@code rules/domains/scm.md} § Standard Error Codes.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    // ---------------- 404: NOT_FOUND family ----------------

    @Test
    @DisplayName("PoNotFoundException → 404 PO_NOT_FOUND")
    void poNotFound() {
        ResponseEntity<ErrorResponse> r = handler.handlePoNotFound(
                new PoNotFoundException("PO not found: po-001"));
        assertStatus(r, HttpStatus.NOT_FOUND, "PO_NOT_FOUND");
        assertThat(r.getBody().message()).contains("po-001");
    }

    @Test
    @DisplayName("SupplierNotFoundException → 404 SUPPLIER_NOT_FOUND")
    void supplierNotFound() {
        ResponseEntity<ErrorResponse> r = handler.handleSupplierNotFound(
                new SupplierNotFoundException("Supplier not found: sup-001"));
        assertStatus(r, HttpStatus.NOT_FOUND, "SUPPLIER_NOT_FOUND");
    }

    // ---------------- 422: UNPROCESSABLE_ENTITY family ----------------

    @Test
    @DisplayName("PoStatusTransitionInvalidException → 422 with from/to/actor details")
    void statusTransitionInvalidIncludesDetails() {
        // The one arm that keeps the `details`-carrying ApiErrorBody extension
        // (TASK-SCM-BE-055 design decision 1) — every other arm returns ErrorResponse.
        ResponseEntity<ApiErrorBody> r = handler.handleStatusInvalid(
                new PoStatusTransitionInvalidException(
                        PoStatus.DRAFT, PoStatus.RECEIVED, ActorType.SYSTEM));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(r.getBody()).isNotNull();
        assertThat(r.getBody().code()).isEqualTo("PO_STATUS_TRANSITION_INVALID");
        assertThat(r.getBody().timestamp()).isNotNull();
        assertThat(r.getBody().details())
                .containsEntry("from", "DRAFT")
                .containsEntry("to", "RECEIVED")
                .containsEntry("actor", "SYSTEM");
    }

    @Test
    @DisplayName("PoAlreadyConfirmedException → 422 PO_ALREADY_CONFIRMED")
    void alreadyConfirmed() {
        ResponseEntity<ErrorResponse> r = handler.handleAlreadyConfirmed(
                new PoAlreadyConfirmedException("po already confirmed"));
        assertStatus(r, HttpStatus.UNPROCESSABLE_ENTITY, "PO_ALREADY_CONFIRMED");
    }

    @Test
    @DisplayName("PoQuantityExceededException → 422 PO_QUANTITY_EXCEEDED")
    void quantityExceeded() {
        ResponseEntity<ErrorResponse> r = handler.handleQuantityExceeded(
                new PoQuantityExceededException("ordered > confirmed"));
        assertStatus(r, HttpStatus.UNPROCESSABLE_ENTITY, "PO_QUANTITY_EXCEEDED");
    }

    @Test
    @DisplayName("AsnOverreceiptException → 422 ASN_OVERRECEIPT")
    void asnOverreceipt() {
        ResponseEntity<ErrorResponse> r = handler.handleOverreceipt(
                new AsnOverreceiptException("ASN qty exceeds line balance"));
        assertStatus(r, HttpStatus.UNPROCESSABLE_ENTITY, "ASN_OVERRECEIPT");
    }

    @Test
    @DisplayName("SupplierInactiveException → 422 SUPPLIER_INACTIVE")
    void supplierInactive() {
        ResponseEntity<ErrorResponse> r = handler.handleSupplierInactive(
                new SupplierInactiveException("supplier disabled"));
        assertStatus(r, HttpStatus.UNPROCESSABLE_ENTITY, "SUPPLIER_INACTIVE");
    }

    @Test
    @DisplayName("CatalogSkuUnknownException → 422 CATALOG_SKU_UNKNOWN")
    void catalogSkuUnknown() {
        ResponseEntity<ErrorResponse> r = handler.handleSku(
                new CatalogSkuUnknownException("sku-001 not in catalog"));
        assertStatus(r, HttpStatus.UNPROCESSABLE_ENTITY, "CATALOG_SKU_UNKNOWN");
    }

    @Test
    @DisplayName("IdempotencyKeyMismatchException → 422 IDEMPOTENCY_KEY_MISMATCH")
    void idempotencyMismatch() {
        ResponseEntity<ErrorResponse> r = handler.handleIdempotencyMismatch(
                new IdempotencyKeyMismatchException("key collision"));
        assertStatus(r, HttpStatus.UNPROCESSABLE_ENTITY, "IDEMPOTENCY_KEY_MISMATCH");
    }

    @Test
    @DisplayName("IllegalArgumentException → 422 VALIDATION_ERROR")
    void illegalArgument() {
        ResponseEntity<ErrorResponse> r = handler.handleIllegalArgument(
                new IllegalArgumentException("invalid currency"));
        assertStatus(r, HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR");
    }

    @Test
    @DisplayName("IllegalStateException → 422 ILLEGAL_STATE")
    void illegalState() {
        ResponseEntity<ErrorResponse> r = handler.handleIllegalState(
                new IllegalStateException("no actor"));
        assertStatus(r, HttpStatus.UNPROCESSABLE_ENTITY, "ILLEGAL_STATE");
    }

    // ---------------- 503: SERVICE_UNAVAILABLE ----------------

    @Test
    @DisplayName("SupplierUnavailableException → 503 SUPPLIER_UNAVAILABLE")
    void supplierUnavailable() {
        ResponseEntity<ErrorResponse> r = handler.handleSupplierUnavailable(
                new SupplierUnavailableException("circuit OPEN"));
        assertStatus(r, HttpStatus.SERVICE_UNAVAILABLE, "SUPPLIER_UNAVAILABLE");
    }

    // ---------------- 409: CONFLICT family ----------------

    @Test
    @DisplayName("OptimisticLockException → 409 CONCURRENT_MODIFICATION")
    void optimisticLock() {
        ResponseEntity<ErrorResponse> r = handler.handleJpaOptimisticLock(
                new OptimisticLockException("version stale"));
        assertStatus(r, HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION");
    }

    @Test
    @DisplayName("ObjectOptimisticLockingFailureException → 409 CONCURRENT_MODIFICATION")
    void springOptimisticLock() {
        ResponseEntity<ErrorResponse> r = handler.handleOptimisticLock(
                new ObjectOptimisticLockingFailureException("entity", "id"));
        assertStatus(r, HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION");
    }

    @Test
    @DisplayName("Unique violation (SQLSTATE 23505) → 409 CONFLICT")
    void dataIntegrityUniqueViolation() {
        // Real SQLException in the cause chain (not a mock): the discriminant walks
        // the chain for SQLSTATE 23505. The reachability of this SQLSTATE through
        // Spring's exception translation from a real Postgres is proven in
        // DataIntegrityViolationIntegrationTest.
        ResponseEntity<ErrorResponse> r = handler.handleIntegrity(
                new DataIntegrityViolationException("dup key",
                        new SQLException("duplicate key value violates unique constraint", "23505")));
        assertStatus(r, HttpStatus.CONFLICT, "CONFLICT");
    }

    @Test
    @DisplayName("FK violation (SQLSTATE 23503, non-unique) → 500 INTERNAL_ERROR")
    void dataIntegrityForeignKeyViolation() {
        ResponseEntity<ErrorResponse> r = handler.handleIntegrity(
                new DataIntegrityViolationException("fk violation",
                        new SQLException("violates foreign key constraint", "23503")));
        assertStatus(r, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR");
    }

    @Test
    @DisplayName("Data integrity with no SQLState in chain → 500 INTERNAL_ERROR (fail loud, not masked as 409)")
    void dataIntegrityNoSqlState() {
        ResponseEntity<ErrorResponse> r = handler.handleIntegrity(
                new DataIntegrityViolationException("some integrity error"));
        assertStatus(r, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR");
    }

    // ---------------- 400: BAD_REQUEST family ----------------

    @Test
    @DisplayName("Missing Idempotency-Key header → 400 IDEMPOTENCY_KEY_REQUIRED")
    void missingIdempotencyHeader() {
        MissingRequestHeaderException ex = mock(MissingRequestHeaderException.class);
        when(ex.getHeaderName()).thenReturn("Idempotency-Key");
        ResponseEntity<ErrorResponse> r = handler.handleMissingHeader(ex);
        assertStatus(r, HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED");
    }

    @Test
    @DisplayName("Missing Idempotency-Key header is case-insensitive")
    void missingIdempotencyHeaderCaseInsensitive() {
        MissingRequestHeaderException ex = mock(MissingRequestHeaderException.class);
        when(ex.getHeaderName()).thenReturn("idempotency-key");
        ResponseEntity<ErrorResponse> r = handler.handleMissingHeader(ex);
        assertStatus(r, HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED");
    }

    @Test
    @DisplayName("Missing other header → 400 VALIDATION_ERROR")
    void missingOtherHeader() {
        MissingRequestHeaderException ex = mock(MissingRequestHeaderException.class);
        when(ex.getHeaderName()).thenReturn("X-Custom");
        ResponseEntity<ErrorResponse> r = handler.handleMissingHeader(ex);
        assertStatus(r, HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        assertThat(r.getBody().message()).contains("X-Custom");
    }

    @Test
    @DisplayName("MethodArgumentTypeMismatchException → 400 VALIDATION_ERROR")
    void typeMismatch() {
        MethodArgumentTypeMismatchException ex = mock(MethodArgumentTypeMismatchException.class);
        when(ex.getName()).thenReturn("status");
        ResponseEntity<ErrorResponse> r = handler.handleTypeMismatch(ex);
        assertStatus(r, HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        assertThat(r.getBody().message()).contains("status");
    }

    @Test
    @DisplayName("HttpMessageNotReadableException → 400 VALIDATION_ERROR")
    void malformedBody() {
        ResponseEntity<ErrorResponse> r = handler.handleMalformedRequest(
                new HttpMessageNotReadableException("malformed", (org.springframework.http.HttpInputMessage) null));
        assertStatus(r, HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
    }

    // ---------------- ResponseStatusException pass-through ----------------

    @Test
    @DisplayName("ResponseStatusException 401 → 401 UNAUTHORIZED (webhook signature invalid)")
    void responseStatusException401() {
        ResponseEntity<ErrorResponse> r = handler.handleResponseStatus(
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "WEBHOOK_SIGNATURE_INVALID"));
        assertStatus(r, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
        assertThat(r.getBody().message()).contains("WEBHOOK_SIGNATURE_INVALID");
    }

    // ---------------- 500: INTERNAL_SERVER_ERROR ----------------

    @Test
    @DisplayName("Generic Exception → 500 INTERNAL_ERROR (no exception detail leaked)")
    void unexpected() {
        ResponseEntity<ErrorResponse> r = handler.handleGeneral(
                new RuntimeException("secret crash detail"));
        assertStatus(r, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR");
        assertThat(r.getBody().message())
                .as("internal error must not leak exception message")
                .doesNotContain("secret crash detail");
    }

    // ---------------- helpers ----------------

    private static void assertStatus(ResponseEntity<ErrorResponse> r, HttpStatus expected, String code) {
        assertThat(r.getStatusCode()).isEqualTo(expected);
        assertThat(r.getBody()).isNotNull();
        assertThat(r.getBody().code()).isEqualTo(code);
        assertThat(r.getBody().timestamp()).isNotNull();
    }
}
