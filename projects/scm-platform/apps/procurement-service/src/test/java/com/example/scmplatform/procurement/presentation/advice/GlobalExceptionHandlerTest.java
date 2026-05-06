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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GlobalExceptionHandler}. Bypasses Spring MVC entirely
 * and invokes each handler method directly — fastest feedback for a pure
 * mapper that only translates exceptions to {@code ApiErrorBody} envelopes.
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
        ResponseEntity<ApiErrorBody> r = handler.handlePoNotFound(
                new PoNotFoundException("PO not found: po-001"));
        assertStatus(r, HttpStatus.NOT_FOUND, "PO_NOT_FOUND");
        assertThat(r.getBody().message()).contains("po-001");
    }

    @Test
    @DisplayName("SupplierNotFoundException → 404 SUPPLIER_NOT_FOUND")
    void supplierNotFound() {
        ResponseEntity<ApiErrorBody> r = handler.handleSupplierNotFound(
                new SupplierNotFoundException("Supplier not found: sup-001"));
        assertStatus(r, HttpStatus.NOT_FOUND, "SUPPLIER_NOT_FOUND");
    }

    // ---------------- 422: UNPROCESSABLE_ENTITY family ----------------

    @Test
    @DisplayName("PoStatusTransitionInvalidException → 422 with from/to/actor details")
    void statusTransitionInvalidIncludesDetails() {
        ResponseEntity<ApiErrorBody> r = handler.handleStatusInvalid(
                new PoStatusTransitionInvalidException(
                        PoStatus.DRAFT, PoStatus.RECEIVED, ActorType.SYSTEM));
        assertStatus(r, HttpStatus.UNPROCESSABLE_ENTITY, "PO_STATUS_TRANSITION_INVALID");
        assertThat(r.getBody().details())
                .containsEntry("from", "DRAFT")
                .containsEntry("to", "RECEIVED")
                .containsEntry("actor", "SYSTEM");
    }

    @Test
    @DisplayName("PoAlreadyConfirmedException → 422 PO_ALREADY_CONFIRMED")
    void alreadyConfirmed() {
        ResponseEntity<ApiErrorBody> r = handler.handleAlreadyConfirmed(
                new PoAlreadyConfirmedException("po already confirmed"));
        assertStatus(r, HttpStatus.UNPROCESSABLE_ENTITY, "PO_ALREADY_CONFIRMED");
    }

    @Test
    @DisplayName("PoQuantityExceededException → 422 PO_QUANTITY_EXCEEDED")
    void quantityExceeded() {
        ResponseEntity<ApiErrorBody> r = handler.handleQuantityExceeded(
                new PoQuantityExceededException("ordered > confirmed"));
        assertStatus(r, HttpStatus.UNPROCESSABLE_ENTITY, "PO_QUANTITY_EXCEEDED");
    }

    @Test
    @DisplayName("AsnOverreceiptException → 422 ASN_OVERRECEIPT")
    void asnOverreceipt() {
        ResponseEntity<ApiErrorBody> r = handler.handleOverreceipt(
                new AsnOverreceiptException("ASN qty exceeds line balance"));
        assertStatus(r, HttpStatus.UNPROCESSABLE_ENTITY, "ASN_OVERRECEIPT");
    }

    @Test
    @DisplayName("SupplierInactiveException → 422 SUPPLIER_INACTIVE")
    void supplierInactive() {
        ResponseEntity<ApiErrorBody> r = handler.handleSupplierInactive(
                new SupplierInactiveException("supplier disabled"));
        assertStatus(r, HttpStatus.UNPROCESSABLE_ENTITY, "SUPPLIER_INACTIVE");
    }

    @Test
    @DisplayName("CatalogSkuUnknownException → 422 CATALOG_SKU_UNKNOWN")
    void catalogSkuUnknown() {
        ResponseEntity<ApiErrorBody> r = handler.handleSku(
                new CatalogSkuUnknownException("sku-001 not in catalog"));
        assertStatus(r, HttpStatus.UNPROCESSABLE_ENTITY, "CATALOG_SKU_UNKNOWN");
    }

    @Test
    @DisplayName("IdempotencyKeyMismatchException → 422 IDEMPOTENCY_KEY_MISMATCH")
    void idempotencyMismatch() {
        ResponseEntity<ApiErrorBody> r = handler.handleIdempotencyMismatch(
                new IdempotencyKeyMismatchException("key collision"));
        assertStatus(r, HttpStatus.UNPROCESSABLE_ENTITY, "IDEMPOTENCY_KEY_MISMATCH");
    }

    @Test
    @DisplayName("IllegalArgumentException → 422 VALIDATION_ERROR")
    void illegalArgument() {
        ResponseEntity<ApiErrorBody> r = handler.handleIllegalArgument(
                new IllegalArgumentException("invalid currency"));
        assertStatus(r, HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR");
    }

    @Test
    @DisplayName("IllegalStateException → 422 ILLEGAL_STATE")
    void illegalState() {
        ResponseEntity<ApiErrorBody> r = handler.handleIllegalState(
                new IllegalStateException("no actor"));
        assertStatus(r, HttpStatus.UNPROCESSABLE_ENTITY, "ILLEGAL_STATE");
    }

    // ---------------- 503: SERVICE_UNAVAILABLE ----------------

    @Test
    @DisplayName("SupplierUnavailableException → 503 SUPPLIER_UNAVAILABLE")
    void supplierUnavailable() {
        ResponseEntity<ApiErrorBody> r = handler.handleSupplierUnavailable(
                new SupplierUnavailableException("circuit OPEN"));
        assertStatus(r, HttpStatus.SERVICE_UNAVAILABLE, "SUPPLIER_UNAVAILABLE");
    }

    // ---------------- 409: CONFLICT family ----------------

    @Test
    @DisplayName("OptimisticLockException → 409 CONFLICT")
    void optimisticLock() {
        ResponseEntity<ApiErrorBody> r = handler.handleOptimisticLock(
                new OptimisticLockException("version stale"));
        assertStatus(r, HttpStatus.CONFLICT, "CONFLICT");
    }

    @Test
    @DisplayName("ObjectOptimisticLockingFailureException → 409 CONFLICT")
    void springOptimisticLock() {
        ResponseEntity<ApiErrorBody> r = handler.handleOptimisticLock(
                new ObjectOptimisticLockingFailureException("entity", "id"));
        assertStatus(r, HttpStatus.CONFLICT, "CONFLICT");
    }

    @Test
    @DisplayName("DataIntegrityViolationException → 409 CONFLICT")
    void dataIntegrity() {
        ResponseEntity<ApiErrorBody> r = handler.handleIntegrity(
                new DataIntegrityViolationException("unique violation"));
        assertStatus(r, HttpStatus.CONFLICT, "CONFLICT");
    }

    // ---------------- 400: BAD_REQUEST family ----------------

    @Test
    @DisplayName("Missing Idempotency-Key header → 400 IDEMPOTENCY_KEY_REQUIRED")
    void missingIdempotencyHeader() {
        MissingRequestHeaderException ex = mock(MissingRequestHeaderException.class);
        when(ex.getHeaderName()).thenReturn("Idempotency-Key");
        ResponseEntity<ApiErrorBody> r = handler.handleMissingHeader(ex);
        assertStatus(r, HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED");
    }

    @Test
    @DisplayName("Missing Idempotency-Key header is case-insensitive")
    void missingIdempotencyHeaderCaseInsensitive() {
        MissingRequestHeaderException ex = mock(MissingRequestHeaderException.class);
        when(ex.getHeaderName()).thenReturn("idempotency-key");
        ResponseEntity<ApiErrorBody> r = handler.handleMissingHeader(ex);
        assertStatus(r, HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED");
    }

    @Test
    @DisplayName("Missing other header → 400 VALIDATION_ERROR")
    void missingOtherHeader() {
        MissingRequestHeaderException ex = mock(MissingRequestHeaderException.class);
        when(ex.getHeaderName()).thenReturn("X-Custom");
        ResponseEntity<ApiErrorBody> r = handler.handleMissingHeader(ex);
        assertStatus(r, HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        assertThat(r.getBody().message()).contains("X-Custom");
    }

    @Test
    @DisplayName("MethodArgumentTypeMismatchException → 400 VALIDATION_ERROR")
    void typeMismatch() {
        MethodArgumentTypeMismatchException ex = mock(MethodArgumentTypeMismatchException.class);
        when(ex.getName()).thenReturn("status");
        ResponseEntity<ApiErrorBody> r = handler.handleTypeMismatch(ex);
        assertStatus(r, HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        assertThat(r.getBody().message()).contains("status");
    }

    @Test
    @DisplayName("HttpMessageNotReadableException → 400 VALIDATION_ERROR")
    void malformedBody() {
        ResponseEntity<ApiErrorBody> r = handler.handleMalformed(
                new HttpMessageNotReadableException("malformed", (org.springframework.http.HttpInputMessage) null));
        assertStatus(r, HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
    }

    // ---------------- 500: INTERNAL_SERVER_ERROR ----------------

    @Test
    @DisplayName("Generic Exception → 500 INTERNAL_ERROR (no exception detail leaked)")
    void unexpected() {
        ResponseEntity<ApiErrorBody> r = handler.handleGeneral(
                new RuntimeException("secret crash detail"));
        assertStatus(r, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR");
        assertThat(r.getBody().message())
                .as("internal error must not leak exception message")
                .doesNotContain("secret crash detail");
    }

    // ---------------- helpers ----------------

    private static void assertStatus(ResponseEntity<ApiErrorBody> r, HttpStatus expected, String code) {
        assertThat(r.getStatusCode()).isEqualTo(expected);
        assertThat(r.getBody()).isNotNull();
        assertThat(r.getBody().code()).isEqualTo(code);
        assertThat(r.getBody().timestamp()).isNotNull();
    }
}
