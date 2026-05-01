package com.wms.inbound.adapter.in.web.advice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wms.inbound.adapter.in.web.dto.response.ApiErrorEnvelope;
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
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Unit tests for {@link GlobalExceptionHandler}.
 *
 * <p>Each test verifies:
 * <ol>
 *   <li>The correct HTTP status is returned.</li>
 *   <li>The {@code ApiErrorEnvelope.code} equals the contract-defined string
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
        ResponseEntity<ApiErrorEnvelope> resp =
                handler.handleAsnNotFound(new AsnNotFoundException(ANY_UUID));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp.getBody().code()).isEqualTo("ASN_NOT_FOUND");
    }

    @Test
    void inspectionNotFound_returns404_withCode_INSPECTION_NOT_FOUND() {
        ResponseEntity<ApiErrorEnvelope> resp =
                handler.handleInspectionNotFound(new InspectionNotFoundException(ANY_UUID));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp.getBody().code()).isEqualTo("INSPECTION_NOT_FOUND");
    }

    @Test
    void putawayInstructionNotFound_returns404_withCode_PUTAWAY_INSTRUCTION_NOT_FOUND() {
        ResponseEntity<ApiErrorEnvelope> resp =
                handler.handlePutawayInstructionNotFound(
                        new PutawayInstructionNotFoundException(ANY_UUID));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp.getBody().code()).isEqualTo("PUTAWAY_INSTRUCTION_NOT_FOUND");
    }

    @Test
    void putawayLineNotFound_returns404_withCode_PUTAWAY_LINE_NOT_FOUND() {
        ResponseEntity<ApiErrorEnvelope> resp =
                handler.handlePutawayLineNotFound(new PutawayLineNotFoundException(ANY_UUID));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp.getBody().code()).isEqualTo("PUTAWAY_LINE_NOT_FOUND");
    }

    // -------------------------------------------------------------------------
    // 409 Conflict
    // -------------------------------------------------------------------------

    @Test
    void asnNoDuplicate_returns409_withCode_ASN_NO_DUPLICATE() {
        ResponseEntity<ApiErrorEnvelope> resp =
                handler.handleAsnDuplicate(new AsnNoDuplicateException("ASN-001"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resp.getBody().code()).isEqualTo("ASN_NO_DUPLICATE");
    }

    @Test
    void optimisticLock_returns409_withCode_CONFLICT() {
        ResponseEntity<ApiErrorEnvelope> resp =
                handler.handleConflict(new OptimisticLockingFailureException("lock"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resp.getBody().code()).isEqualTo("CONFLICT");
    }

    // -------------------------------------------------------------------------
    // 422 Unprocessable Entity — domain exceptions via InboundDomainException handler
    // -------------------------------------------------------------------------

    @Test
    void stateTransitionInvalid_returns422_withCode_STATE_TRANSITION_INVALID() {
        ResponseEntity<ApiErrorEnvelope> resp =
                handler.handleDomainException(
                        new StateTransitionInvalidException("CREATED", "CLOSED"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(resp.getBody().code()).isEqualTo("STATE_TRANSITION_INVALID");
    }

    @Test
    void asnAlreadyClosed_returns422_withCode_ASN_ALREADY_CLOSED() {
        ResponseEntity<ApiErrorEnvelope> resp =
                handler.handleDomainException(
                        new AsnAlreadyClosedException(ANY_UUID, "CANCELLED"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(resp.getBody().code()).isEqualTo("ASN_ALREADY_CLOSED");
    }

    @Test
    void inspectionQuantityMismatch_returns422_withCode_INSPECTION_QUANTITY_MISMATCH() {
        ResponseEntity<ApiErrorEnvelope> resp =
                handler.handleDomainException(
                        new InspectionQuantityMismatchException(ANY_UUID, 10, 15));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(resp.getBody().code()).isEqualTo("INSPECTION_QUANTITY_MISMATCH");
    }

    @Test
    void inspectionIncomplete_returns422_withCode_INSPECTION_INCOMPLETE() {
        ResponseEntity<ApiErrorEnvelope> resp =
                handler.handleDomainException(new InspectionIncompleteException(3));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(resp.getBody().code()).isEqualTo("INSPECTION_INCOMPLETE");
    }

    @Test
    void partnerInvalidType_returns422_withCode_PARTNER_INVALID_TYPE() {
        ResponseEntity<ApiErrorEnvelope> resp =
                handler.handleDomainException(
                        new PartnerInvalidTypeException(ANY_UUID, "not a supplier"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(resp.getBody().code()).isEqualTo("PARTNER_INVALID_TYPE");
    }

    @Test
    void skuInactive_returns422_withCode_SKU_INACTIVE() {
        ResponseEntity<ApiErrorEnvelope> resp =
                handler.handleDomainException(new SkuInactiveException(ANY_UUID));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(resp.getBody().code()).isEqualTo("SKU_INACTIVE");
    }

    @Test
    void lotRequired_returns422_withCode_LOT_REQUIRED() {
        ResponseEntity<ApiErrorEnvelope> resp =
                handler.handleDomainException(new LotRequiredException(ANY_UUID));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(resp.getBody().code()).isEqualTo("LOT_REQUIRED");
    }

    @Test
    void warehouseNotFoundInReadModel_returns422_withCode_WAREHOUSE_NOT_FOUND() {
        ResponseEntity<ApiErrorEnvelope> resp =
                handler.handleDomainException(
                        new WarehouseNotFoundInReadModelException(ANY_UUID));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(resp.getBody().code()).isEqualTo("WAREHOUSE_NOT_FOUND");
    }

    @Test
    void putawayQuantityExceeded_returns422_withCode_PUTAWAY_QUANTITY_EXCEEDED() {
        ResponseEntity<ApiErrorEnvelope> resp =
                handler.handleDomainException(
                        new PutawayQuantityExceededException(ANY_UUID, 100, 80));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(resp.getBody().code()).isEqualTo("PUTAWAY_QUANTITY_EXCEEDED");
    }

    @Test
    void locationInactive_returns422_withCode_LOCATION_INACTIVE() {
        ResponseEntity<ApiErrorEnvelope> resp =
                handler.handleDomainException(new LocationInactiveException(ANY_UUID));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(resp.getBody().code()).isEqualTo("LOCATION_INACTIVE");
    }

    @Test
    void warehouseMismatch_returns422_withCode_WAREHOUSE_MISMATCH() {
        ResponseEntity<ApiErrorEnvelope> resp =
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
        ResponseEntity<ApiErrorEnvelope> resp =
                handler.handleForbidden(new AccessDeniedException("denied"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resp.getBody().code()).isEqualTo("FORBIDDEN");
    }

    @Test
    void authorizationDenied_returns403_withCode_FORBIDDEN() {
        ResponseEntity<ApiErrorEnvelope> resp =
                handler.handleForbidden(
                        new AuthorizationDeniedException("denied",
                                () -> false));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resp.getBody().code()).isEqualTo("FORBIDDEN");
    }

    // -------------------------------------------------------------------------
    // 400 Bad Request
    // -------------------------------------------------------------------------

    @Test
    void illegalArgument_returns400_withCode_VALIDATION_ERROR() {
        ResponseEntity<ApiErrorEnvelope> resp =
                handler.handleBadInput(new IllegalArgumentException("bad"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().code()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void methodArgumentTypeMismatch_returns400_withCode_VALIDATION_ERROR() throws Exception {
        MethodArgumentTypeMismatchException ex =
                new MethodArgumentTypeMismatchException("foo", UUID.class, "id", null, null);
        ResponseEntity<ApiErrorEnvelope> resp = handler.handleBadInput(ex);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().code()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void methodArgumentNotValid_returns400_withCode_VALIDATION_ERROR() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getMessage()).thenReturn("Validation failed");

        ResponseEntity<ApiErrorEnvelope> resp = handler.handleBadInput(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().code()).isEqualTo("VALIDATION_ERROR");
    }

    // -------------------------------------------------------------------------
    // 500 Fallback
    // -------------------------------------------------------------------------

    @Test
    void unknownException_returns500_withCode_INTERNAL_ERROR() {
        ResponseEntity<ApiErrorEnvelope> resp =
                handler.handleUnknown(new RuntimeException("unexpected"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(resp.getBody().code()).isEqualTo("INTERNAL_ERROR");
    }
}
