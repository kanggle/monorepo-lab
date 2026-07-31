package com.wms.inbound.adapter.in.web.advice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.web.dto.ErrorResponse;
import com.wms.inbound.domain.exception.AsnAlreadyClosedException;
import com.wms.inbound.domain.exception.AsnNoDuplicateException;
import com.wms.inbound.domain.exception.AsnNotFoundException;
import com.wms.inbound.domain.exception.InspectionIncompleteException;
import com.wms.inbound.domain.exception.InspectionNotFoundException;
import com.wms.inbound.domain.exception.InspectionQuantityMismatchException;
import com.wms.inbound.domain.exception.LocationInactiveException;
import com.wms.inbound.domain.exception.LotRequiredException;
import com.wms.inbound.domain.exception.PartnerInvalidTypeException;
import com.wms.inbound.domain.exception.PutawayInstructionNotFoundException;
import com.wms.inbound.domain.exception.PutawayLineNotFoundException;
import com.wms.inbound.domain.exception.PutawayQuantityExceededException;
import com.wms.inbound.domain.exception.SkuInactiveException;
import com.wms.inbound.domain.exception.StateTransitionInvalidException;
import com.wms.inbound.domain.exception.WarehouseMismatchException;
import com.wms.inbound.domain.exception.WarehouseNotFoundInReadModelException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Unit tests for {@link GlobalExceptionHandler}.
 *
 * <p>Each test verifies:
 * <ol>
 *   <li>The correct HTTP status is returned.</li>
 *   <li>The {@code ErrorResponse.code} equals the contract-defined string
 *       from {@code inbound-service-api.md} §"Error Codes".</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private static final UUID ANY_UUID = UUID.randomUUID();

    // -------------------------------------------------------------------------
    // 404 Not Found
    // -------------------------------------------------------------------------

    @Test
    void asnNotFound_returns404_withCode_ASN_NOT_FOUND() {
        ResponseEntity<ErrorResponse> resp =
                handler.handleDomainException(new AsnNotFoundException(ANY_UUID));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp.getBody().code()).isEqualTo("ASN_NOT_FOUND");
    }

    @Test
    void inspectionNotFound_returns404_withCode_INSPECTION_NOT_FOUND() {
        ResponseEntity<ErrorResponse> resp =
                handler.handleDomainException(new InspectionNotFoundException(ANY_UUID));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp.getBody().code()).isEqualTo("INSPECTION_NOT_FOUND");
    }

    @Test
    void putawayInstructionNotFound_returns404_withCode_PUTAWAY_INSTRUCTION_NOT_FOUND() {
        ResponseEntity<ErrorResponse> resp =
                handler.handleDomainException(
                        new PutawayInstructionNotFoundException(ANY_UUID));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp.getBody().code()).isEqualTo("PUTAWAY_INSTRUCTION_NOT_FOUND");
    }

    @Test
    void putawayLineNotFound_returns404_withCode_PUTAWAY_LINE_NOT_FOUND() {
        ResponseEntity<ErrorResponse> resp =
                handler.handleDomainException(new PutawayLineNotFoundException(ANY_UUID));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp.getBody().code()).isEqualTo("PUTAWAY_LINE_NOT_FOUND");
    }

    // -------------------------------------------------------------------------
    // 409 Conflict
    // -------------------------------------------------------------------------

    @Test
    void asnNoDuplicate_returns409_withCode_ASN_NO_DUPLICATE() {
        ResponseEntity<ErrorResponse> resp =
                handler.handleDomainException(new AsnNoDuplicateException("ASN-001"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resp.getBody().code()).isEqualTo("ASN_NO_DUPLICATE");
    }

    @Test
    void optimisticLock_returns409_withCode_CONFLICT() {
        ResponseEntity<ErrorResponse> resp =
                handler.handleConflict(new OptimisticLockingFailureException("lock"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resp.getBody().code()).isEqualTo("CONFLICT");
    }

    // -------------------------------------------------------------------------
    // 422 Unprocessable Entity — domain exceptions via InboundDomainException handler
    // -------------------------------------------------------------------------

    @Test
    void stateTransitionInvalid_returns422_withCode_STATE_TRANSITION_INVALID() {
        ResponseEntity<ErrorResponse> resp =
                handler.handleDomainException(
                        new StateTransitionInvalidException("CREATED", "CLOSED"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(resp.getBody().code()).isEqualTo("STATE_TRANSITION_INVALID");
    }

    @Test
    void asnAlreadyClosed_returns422_withCode_ASN_ALREADY_CLOSED() {
        ResponseEntity<ErrorResponse> resp =
                handler.handleDomainException(
                        new AsnAlreadyClosedException(ANY_UUID, "CANCELLED"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(resp.getBody().code()).isEqualTo("ASN_ALREADY_CLOSED");
    }

    @Test
    void inspectionQuantityMismatch_returns422_withCode_INSPECTION_QUANTITY_MISMATCH() {
        ResponseEntity<ErrorResponse> resp =
                handler.handleDomainException(
                        new InspectionQuantityMismatchException(ANY_UUID, 10, 15));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(resp.getBody().code()).isEqualTo("INSPECTION_QUANTITY_MISMATCH");
    }

    @Test
    void inspectionIncomplete_returns422_withCode_INSPECTION_INCOMPLETE() {
        ResponseEntity<ErrorResponse> resp =
                handler.handleDomainException(new InspectionIncompleteException(3));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(resp.getBody().code()).isEqualTo("INSPECTION_INCOMPLETE");
    }

    @Test
    void partnerInvalidType_returns422_withCode_PARTNER_INVALID_TYPE() {
        ResponseEntity<ErrorResponse> resp =
                handler.handleDomainException(
                        new PartnerInvalidTypeException(ANY_UUID, "not a supplier"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(resp.getBody().code()).isEqualTo("PARTNER_INVALID_TYPE");
    }

    @Test
    void skuInactive_returns422_withCode_SKU_INACTIVE() {
        ResponseEntity<ErrorResponse> resp =
                handler.handleDomainException(new SkuInactiveException(ANY_UUID));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(resp.getBody().code()).isEqualTo("SKU_INACTIVE");
    }

    @Test
    void lotRequired_returns422_withCode_LOT_REQUIRED() {
        ResponseEntity<ErrorResponse> resp =
                handler.handleDomainException(new LotRequiredException(ANY_UUID));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(resp.getBody().code()).isEqualTo("LOT_REQUIRED");
    }

    @Test
    void warehouseNotFoundInReadModel_returns422_withCode_WAREHOUSE_NOT_FOUND() {
        ResponseEntity<ErrorResponse> resp =
                handler.handleDomainException(
                        new WarehouseNotFoundInReadModelException(ANY_UUID));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(resp.getBody().code()).isEqualTo("WAREHOUSE_NOT_FOUND");
    }

    @Test
    void putawayQuantityExceeded_returns422_withCode_PUTAWAY_QUANTITY_EXCEEDED() {
        ResponseEntity<ErrorResponse> resp =
                handler.handleDomainException(
                        new PutawayQuantityExceededException(ANY_UUID, 100, 80));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(resp.getBody().code()).isEqualTo("PUTAWAY_QUANTITY_EXCEEDED");
    }

    @Test
    void locationInactive_returns422_withCode_LOCATION_INACTIVE() {
        ResponseEntity<ErrorResponse> resp =
                handler.handleDomainException(new LocationInactiveException(ANY_UUID));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(resp.getBody().code()).isEqualTo("LOCATION_INACTIVE");
    }

    @Test
    void warehouseMismatch_returns422_withCode_WAREHOUSE_MISMATCH() {
        ResponseEntity<ErrorResponse> resp =
                handler.handleDomainException(
                        new WarehouseMismatchException(ANY_UUID, UUID.randomUUID()));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(resp.getBody().code()).isEqualTo("WAREHOUSE_MISMATCH");
    }

    // -------------------------------------------------------------------------
    // 403 Forbidden
    // -------------------------------------------------------------------------

    @Test
    void accessDenied_returns403_withCode_FORBIDDEN() {
        ResponseEntity<ErrorResponse> resp =
                handler.handleForbidden(new AccessDeniedException("denied"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resp.getBody().code()).isEqualTo("FORBIDDEN");
    }

    @Test
    void authorizationDenied_returns403_withCode_FORBIDDEN() {
        ResponseEntity<ErrorResponse> resp =
                handler.handleForbidden(
                        new AuthorizationDeniedException("denied",
                                () -> false));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resp.getBody().code()).isEqualTo("FORBIDDEN");
    }

    // -------------------------------------------------------------------------
    // 400 Bad Request
    // -------------------------------------------------------------------------

    /** Now the arm inherited from {@code CommonGlobalExceptionHandler} (ADR-MONO-058 § D2). */
    @Test
    void illegalArgument_returns400_withCode_VALIDATION_ERROR() {
        ResponseEntity<ErrorResponse> resp =
                handler.handleIllegalArgument(new IllegalArgumentException("bad"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().code()).isEqualTo("VALIDATION_ERROR");
    }

    /** Stays service-local — {@code CommonGlobalExceptionHandler} has no arm for this type. */
    @Test
    void methodArgumentTypeMismatch_returns400_withCode_VALIDATION_ERROR() throws Exception {
        MethodArgumentTypeMismatchException ex =
                new MethodArgumentTypeMismatchException("foo", UUID.class, "id", null, null);
        ResponseEntity<ErrorResponse> resp = handler.handleTypeMismatch(ex);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().code()).isEqualTo("VALIDATION_ERROR");
    }

    /**
     * Now the arm inherited from {@code CommonGlobalExceptionHandler}. Its message is the
     * first field error ({@code "field: reason"}) rather than the raw, verbose
     * {@code MethodArgumentNotValidException#getMessage()} this service emitted before
     * TASK-BE-567. Status (400) and code (VALIDATION_ERROR) — the two things
     * {@code inbound-service-api.md} pins — are unchanged.
     */
    @Test
    void methodArgumentNotValid_returns400_withCode_VALIDATION_ERROR() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors())
                .thenReturn(List.of(new FieldError("request", "qtyPassed", "must not be null")));

        ResponseEntity<ErrorResponse> resp = handler.handleValidation(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().code()).isEqualTo("VALIDATION_ERROR");
        assertThat(resp.getBody().message()).isEqualTo("qtyPassed: must not be null");
    }

    // -------------------------------------------------------------------------
    // 500 Fallback
    // -------------------------------------------------------------------------

    /** Now the catch-all inherited from {@code CommonGlobalExceptionHandler}. */
    @Test
    void unknownException_returns500_withCode_INTERNAL_ERROR() {
        ResponseEntity<ErrorResponse> resp =
                handler.handleGeneral(new RuntimeException("unexpected"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(resp.getBody().code()).isEqualTo("INTERNAL_ERROR");
    }
}
